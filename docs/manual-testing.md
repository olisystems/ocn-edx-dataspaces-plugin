# Manual testing

End-to-end checks against a local OCN Node with this plugin loaded. Prefer [Postman](../postman/) for repeated runs; use the curls below for a quick smoke test.

Defaults assume:

- Node: `http://localhost:9999`
- API prefix: `ocn-v2`
- CPO: `DE` / `CPO`
- Sample CDR: [`examples/sample-ocpi-cdr.json`](examples/sample-ocpi-cdr.json)

## Prerequisites

1. Node running with the EDX plugin JAR in `plugins/`
2. Configured at least:

```properties
edx.cdr.service.baseUrl=https://your-cdr-service.example.com/api
edx.cdr.service.apiKey=<key>
```

3. Optional:

```properties
edx.edc.management.enabled=true
edx.edc.management.baseUrl=http://your-connector/api/management
edx.edc.management.apiKey=<key>

edx.co2.provider.publicApiUrl=https://your-co2-provider.example.com/public/emissions
edx.co2.provider.token=<token>
```

4. An OCPI Token C that can write CDRs for the from-party (and, for dataspace calls, that owns the CPO role).

```bash
# Plain Token C → Authorization header value
export TOKEN_C='your-plain-token-c'
export AUTH="Authorization: Token $(printf '%s' "$TOKEN_C" | base64 -w0)"
export OCN=http://localhost:9999/ocn-v2
```

## 1. Send a CDR (OCPI)

Adjust the OCPI path to match your node’s CDR receiver. Example shape:

```bash
curl -sS -X POST "$OCN/ocpi/receiver/2.2.1/cdrs" \
  -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -H 'OCPI-from-country-code: DE' \
  -H 'OCPI-from-party-id: CPO' \
  -H 'OCPI-to-country-code: DE' \
  -H 'OCPI-to-party-id: EMS' \
  --data @docs/examples/sample-ocpi-cdr.json
```

Expect OCPI `status_code` **1000**. The plugin then ingests asynchronously and stores a mapping when `rawRecordId` is returned.

## 2. CDR lookup (plugin)

```bash
CDR_ID=94864128-50eb-46c6-bd9f-f4323cd4c057

# Mapping
curl -sS "$OCN/plugin/edx/cdrs/mapping?countryCode=DE&partyId=CPO&cdrId=$CDR_ID"

# Resolve raw CDR
curl -sS "$OCN/plugin/edx/cdrs/resolve?countryCode=DE&partyId=CPO&cdrId=$CDR_ID"

# By service id (from mapping.serviceId)
curl -sS "$OCN/plugin/edx/cdrs/raw/$SERVICE_ID"
curl -sS "$OCN/plugin/edx/cdrs/mapping/by-service-id/$SERVICE_ID"
```

## 3. CO₂-enriched CDR

Requires `edx.co2.provider.publicApiUrl`.

```bash
curl -sS "$OCN/plugin/edx/cdrs/enriched?countryCode=DE&partyId=CPO&cdrId=$CDR_ID&zone=DE_LU"
```

Each `charging_period` should include a `co2` object; the CDR should include `co2_total_gco2eq`.

### Grid proxy

```bash
curl -sS -G "$OCN/plugin/edx/co2" \
  --data-urlencode 'start=2025-02-18T09:00:00Z' \
  --data-urlencode 'end=2025-02-18T18:00:00Z' \
  --data-urlencode 'zone=DE_LU' \
  --data-urlencode 'time-resolution=Hourly' \
  --data-urlencode 'calculation-type=Consumption' \
  --data-urlencode 'emission-type=Lifecycle'
```

## 4. Dataspace (EDC)

Requires `edx.edc.management.enabled=true` and a prior successful ingest for that CPO (asset auto-provisioned).

```bash
# Asset mapping
curl -sS -H "$AUTH" "$OCN/plugin/edx/dataspace/cpos/DE/CPO"

# Append a market partner consumer
curl -sS -H "$AUTH" -H 'Content-Type: application/json' \
  -X POST "$OCN/plugin/edx/dataspace/cpos/DE/CPO/policy/consumers" \
  -d '{"consumers":[{"type":"MARKET_PARTNER","id":"4045399000008"}]}'

# Append a DID consumer
curl -sS -H "$AUTH" -H 'Content-Type: application/json' \
  -X POST "$OCN/plugin/edx/dataspace/cpos/DE/CPO/policy/consumers" \
  -d '{"consumers":[{"type":"DID","id":"did:web:example.com:consumer"}]}'

# Replace full list
curl -sS -H "$AUTH" -H 'Content-Type: application/json' \
  -X PUT "$OCN/plugin/edx/dataspace/cpos/DE/CPO/policy" \
  -d '{"consumers":[{"type":"DID","id":"did:web:example.com:consumer"}]}'
```

## Checklist

- [ ] CDR OCPI write returns `1000`
- [ ] Mapping appears under `/plugin/edx/cdrs/mapping`
- [ ] Resolve returns the raw CDR
- [ ] Enriched CDR includes per-period `co2` (if CO₂ configured)
- [ ] Dataspace GET returns asset / policy ids (if EDC configured)
- [ ] Policy POST/PUT updates `allowedConsumersJson` and EDC policies

## Related

- Architecture: [`architecture.md`](architecture.md)
- Postman: [`../postman/`](../postman/)
- Sample CDR: [`examples/sample-ocpi-cdr.json`](examples/sample-ocpi-cdr.json)
