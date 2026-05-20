# EDX CDR Forwarder for OCN Node

The EDX CDR Forwarder is an official OCN Node plugin that sends CDRs seen by the node to a configured CDR service.

Use this plugin when you want the node to keep its normal OCPI CDR behavior and also deliver a copy of each CDR to an external service for storage, processing, or reporting.

## What It Does

- Listens for CDRs passing through the node's standard OCPI CDR endpoints.
- Forwards each captured CDR to the configured CDR service.
- Adds the configured API key as an `x-api-key` header.
- Leaves the node's normal OCPI forwarding behavior unchanged.

## Installation

Place the plugin JAR in the node plugin directory:

```bash
mkdir -p plugins
cp ocn-node-official-plugin-*.jar plugins/edx.jar
```

Start the node with plugin loading enabled for that directory. For a local node, this is commonly configured with:

```properties
ocn.plugins.dir=plugins
```

After startup, the node logs should include the plugin in the `PLUGINS` section.

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

This repository builds the plugin as a Java SPI plugin for `ocn-node-v2`. The plugin compiles against the node plugin API and does not package the node or Spring runtime into its JAR.

Build and test locally:

```bash
./gradlew clean test jar
```

When this repository is checked out next to `ocn-node-v2`, Gradle uses a composite build so local node plugin API changes are available immediately.

To compile against a different node checkout:

```bash
OCN_NODE_REPO=/path/to/ocn-node-v2 ./gradlew clean test jar
```

The plugin id is `edx`, and the service entrypoint is:

```text
edx.connector.ConnectorPlugin
```
