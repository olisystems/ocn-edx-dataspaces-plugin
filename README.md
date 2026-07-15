# EDX CDR Forwarder for OCN Node

The EDX CDR Forwarder is an official OCN Node plugin that sends CDRs seen by the node to a configured CDR service.

Use this plugin when you want the node to keep its normal OCPI CDR behavior and also deliver a copy of each CDR to an external service for storage, processing, or reporting.

## What It Does

- Listens for CDRs passing through the node's standard OCPI CDR endpoints.
- Forwards a CDR to the configured CDR service only when the OCPI response payload `status_code` is **1000** (success).
- Adds the configured API key as an `x-api-key` header.
- Leaves the node's normal OCPI forwarding behavior unchanged.

## Docs

- Architecture (ingest, EDC, enrich, dataspace): [`docs/architecture.md`](docs/architecture.md)
- Manual testing (curl): [`docs/manual-testing.md`](docs/manual-testing.md)
- Postman: [`postman/README.md`](postman/README.md)

## Installation

### Local

```bash
./gradlew jar
mkdir -p ../../ocn-node-v2/plugins
cp build/libs/ocn-node-edx-plugin*.jar ../../ocn-node-v2/plugins/ocn-node-edx-plugin.jar
cd ../../ocn-node-v2 && ./gradlew bootRun
```

The node loads JARs from `-Dloader.path=…` (default `/app/plugins` in Docker).

### Cluster (OTC OBS bucket)

Upload the JAR to the node's OTC OBS bucket (S3-compatible), then restart the node so the entrypoint re-fetches plugins into `/app/plugins`.

No plugins PVC or volume mount is required: the `ocn-node-v2` Docker entrypoint downloads each id in `OCN_PLUGINS` from OBS into `/app/plugins` before the JVM starts (see `ocn-node-v2` `PLUGINS.md`). Plugin loading is configured in the **node** Helm chart (`ocn-node-v2`), not in this plugin repo. Int already sets this in `values.int.yaml` (`OCN_PLUGINS=edx-v1` + OTC credentials). Dev / other envs must have the same env vars before the steps below will load a JAR.

The object key must match the plugin id in `OCN_PLUGINS` with a `.jar` suffix.

Prerequisites (in `ocn-node-v2` Helm / namespace secrets):

- `OCN_PLUGINS` set to the plugin id(s) to fetch (e.g. `edx-v1`)
- `OTC_BUCKET_NAME` plus Secret `otc-obs-credentials` (`access_key_id`, `secret_access_key`)
- Optional: `OTC_ENDPOINT_URL` (default `https://obs.eu-de.otc.t-systems.com`)

1. Build the JAR locally:
   ```bash
   ./gradlew jar
   ```
2. Upload to the same bucket the node uses (`OTC_BUCKET_NAME`), with the same plugin id as `OCN_PLUGINS`:
   ```bash
   # use the bucket/endpoint from the target deployment
   export OTC_BUCKET_NAME=ocn-node-plugins
   export OTC_ENDPOINT_URL=https://obs.eu-de.otc.t-systems.com
   aws s3 cp build/libs/ocn-node-edx-plugin*.jar \
     "s3://${OTC_BUCKET_NAME}/edx-v1.jar" \
     --endpoint-url "$OTC_ENDPOINT_URL"
   ```
3. Confirm the target env sets `OCN_PLUGINS=edx-v1` (and OBS credentials as above).
4. Rollout-restart the concrete node deployment so the entrypoint pulls the new JAR:
   ```bash
   # int
   kubectl -n <ns> rollout restart deployment/ocn-node-v2-int
   # dev
   kubectl -n <ns> rollout restart deployment/ocn-node-v2-dev
   ```

After restart, check the node **PLUGINS** banner and:

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://banula-int.oli-system.com/ocn-v2/plugin/edx/cdrs
```

## Configuration

The CDR service URL is required. The plugin does not provide a default URL.

Add these values to the node's application configuration:

```properties
edx.cdr.service.baseUrl=https://your-cdr-service.example.com
edx.cdr.service.apiKey=your-api-key
```

You can also provide them as environment variables:

```bash
export EDX_CDR_SERVICE_BASE_URL=https://your-cdr-service.example.com
export EDX_CDR_SERVICE_API_KEY=your-api-key
```

Optional settings:

| Setting | Environment Variable | Default |
|---------|----------------------|---------|
| `edx.cdr.service.enabled` | `EDX_CDR_SERVICE_ENABLED` | `true` |
| `edx.cdr.service.timeoutMs` | `EDX_CDR_SERVICE_TIMEOUT_MS` | `5000` |
| `edx.connector.allowed-origins` | `EDX_CONNECTOR_ALLOWED_ORIGINS` | `*` |
| `edx.edc.management.allowed-origins` | `EDX_EDC_MANAGEMENT_ALLOWED_ORIGINS` | `*` |

## CDR ingest id mapping

After ingest, the plugin stores a mapping in the node Postgres database (`edx_cdr_ingest_mapping`) whenever the CDR service returns a `rawRecordId` (the raw CDR was stored). Ingest may respond with `success=false` and `extractionStatus=FAILED` when field extraction failed; the mapping is still saved because enrichment reads the raw record by `rawRecordId`.

| Column | Description |
|--------|-------------|
| `country_code` | OCPI CDR `country_code` |
| `party_id` | OCPI CDR `party_id` |
| `cdr_id` | OCPI CDR `id` |
| `service_id` | CDR service `rawRecordId` returned by ingest |

Lookup:

```bash
curl "http://localhost:9999/ocn-v2/plugin/edx/cdrs/mapping?countryCode=DE&partyId=FHG&cdrId=94864128-50eb-46c6-bd9f-f4323cd4c057"
curl "http://localhost:9999/ocn-v2/plugin/edx/cdrs/mapping/by-service-id/6a13e59caab623818f95d628"
```

Resolve fetches the raw CDR via stored mapping:

```bash
curl "http://localhost:9999/ocn-v2/plugin/edx/cdrs/resolve?countryCode=DE&partyId=FHG&cdrId=94864128-50eb-46c6-bd9f-f4323cd4c057"
```

## CO₂-enriched CDR

When the CO₂ emissions provider is configured, the plugin builds enrichment server-side:

1. Load raw CDR from ingest service (via DB mapping)
2. Fetch grid intensity from the CO₂ provider for the charging window
3. Add a `co2` object on each `charging_period` and `co2_total_gco2eq` on the CDR

```bash
curl "http://localhost:9999/ocn-v2/plugin/edx/cdrs/enriched?countryCode=DE&partyId=FHG&cdrId=94864128-50eb-46c6-bd9f-f4323cd4c057&zone=DE_LU"
```

Example charging period after enrichment:

```json
{
  "start_date_time": "2026-02-18T09:15:00Z",
  "dimensions": [{ "type": "ENERGY", "volume": 8.5 }],
  "co2": {
    "grid_zone": "DE_LU",
    "unit": "gCO2eqPerkWh",
    "intensity_gco2eq_per_kwh": 487.0,
    "emissions_gco2eq": 4139.5,
    "intensity_timestamp": "2026-02-18T09:00:00Z"
  }
}
```

## CDR read endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `{apiPrefix}/plugin/edx/cdrs/enriched` | CO₂-enriched CDR (requires CO₂ provider) |
| `GET` | `{apiPrefix}/plugin/edx/cdrs/resolve` | Raw ingested CDR via mapping |
| `GET` | `{apiPrefix}/plugin/edx/cdrs/raw/{serviceId}` | Raw CDR by ingest service id |
| `GET` | `{apiPrefix}/plugin/edx/cdrs/mapping` | OCPI identity → service id mapping |

## CO2 emission data proxy (optional)

When `edx.co2.provider.publicApiUrl` is set, the plugin also exposes a raw grid-data proxy:

```http
GET {apiPrefix}/plugin/edx/co2?start=2025-02-18T09:00:00Z&end=2025-02-18T18:00:00Z&zone=DE_LU&time-resolution=Hourly&calculation-type=Consumption&emission-type=Lifecycle
```

The plugin forwards the request to the configured provider public API with the same query parameters and an `Authorization` header.

Configuration:

```properties
edx.co2.provider.publicApiUrl=https://your-co2-provider.example.com/public/emissions
edx.co2.provider.token=co2-token
```

Environment variables:

```bash
export EDX_CO2_PROVIDER_PUBLIC_API_URL=https://your-co2-provider.example.com/public/emissions
export EDX_CO2_PROVIDER_TOKEN=co2-token
```

| Setting | Environment Variable | Default |
|---------|----------------------|---------|
| `edx.co2.provider.timeoutMs` | `EDX_CO2_PROVIDER_TIMEOUT_MS` | `5000` |

The provider response is mapped to `Co2EmissionDataResponseDto` (`startUtc`, `stopUtc`, `measurements[]` with `measurementValues[]`) and returned as JSON from the node endpoint.

Example:

```bash
curl -G "http://localhost:8080/ocn-v2/plugin/edx/co2" \
  --data-urlencode "start=2025-02-18T09:00:00Z" \
  --data-urlencode "end=2025-02-18T18:00:00Z" \
  --data-urlencode "zone=DE_LU" \
  --data-urlencode "time-resolution=Hourly" \
  --data-urlencode "calculation-type=Consumption" \
  --data-urlencode "emission-type=Lifecycle"
```

If `edx.cdr.service.baseUrl` or `EDX_CDR_SERVICE_BASE_URL` is missing, the plugin fails to load with a clear configuration error.

## Verification

On startup, look for a log line similar to:

```text
EDX CDR service connector registered; baseUrl=https://your-cdr-service.example.com, apiKeyConfigured=true
```

The API key is never printed in full. Startup logs only show whether it is configured.

When a CDR is received by the node, the plugin forwards it asynchronously. If the CDR service rejects the request or cannot be reached, the node logs a warning.

## Troubleshooting

If the plugin does not appear in the node startup logs, check that the JAR is in the configured plugin directory and that plugin loading is enabled.

If the plugin reports missing configuration, set `edx.cdr.service.baseUrl` in the node configuration or `EDX_CDR_SERVICE_BASE_URL` in the node environment.

If forwarding fails with an SSL certificate error, make sure the Java runtime used by the node trusts the CDR service certificate chain.

## Contributing

This repository builds a Spring Boot auto-configuration plugin for `ocn-node-v2`. The plugin compiles against the node plugin API and does not package the node or Spring runtime into its JAR.

Build and test locally:

```bash
./gradlew clean test jar
```

When this repository is checked out next to `ocn-node-v2`, Gradle uses a composite build so local node plugin API changes are available immediately.

To compile against a different node checkout:

```bash
OCN_NODE_REPO=/path/to/ocn-node-v2 ./gradlew clean test jar
```

The plugin id is `edx`, and the auto-configuration entrypoint is:

```text
edx.connector.EdxConnectorAutoConfiguration
```
