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
   - `test_did` — a DID controlled by your local EDC / identity provider (disabled by default)
   - `cdr_service_base` — enable and set to your local CDR service before using folders 04
4. Point `ocn_base` at your node (default `http://localhost:9999/ocn-v2`).
5. Point `edc_management_base` at your local EDC management API (default `http://localhost:8181/api/management`).

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
