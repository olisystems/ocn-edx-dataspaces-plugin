# Postman

Import both files into Postman (or a compatible client):

| File | Purpose |
|------|---------|
| [`EDX-Plugin-API.postman_collection.json`](EDX-Plugin-API.postman_collection.json) | Plugin HTTP API + optional CDR service / EDC reference calls |
| [`EDX-Plugin-Local.postman_environment.json`](EDX-Plugin-Local.postman_environment.json) | Local defaults and empty secret slots |

## Quick start

1. Import the collection and environment.
2. Select environment **EDX Plugin — Local**.
3. Set:
   - `cpo_token_c` — plain OCPI Token C for the CPO (dataspace folder)
   - `cdr_service_api_key` / `edc_api_key` — only if you use folders 04–05
4. Point `ocn_base` at your node (default `http://localhost:9999/ocn-v2`).
5. Point `cdr_service_base` / `edc_management_base` at your deployments (placeholders use `example.com` / localhost).

## Folders

| Folder | What it covers |
|--------|----------------|
| **01 - CDR Lookup** | Plugin: resolve, raw, mapping, enriched |
| **02 - CO2 Proxy** | Plugin: `/plugin/edx/co2` |
| **03 - Dataspace** | Plugin: CPO asset mapping + policy consumers (auth required) |
| **04 - CDR Service** | Direct ingest/raw/co2-relevant (optional debugging) |
| **05 - EDC Management** | Direct asset/policy/contract-def create (optional; plugin does this automatically) |

Dataspace requests send `Authorization: Token <base64(cpo_token_c)>` via the collection pre-request script.

Also see [`docs/manual-testing.md`](../docs/manual-testing.md) for the same flows as curl.
