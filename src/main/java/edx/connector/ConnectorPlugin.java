/*
    Copyright 2026 OLI Systems GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package edx.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import edx.connector.cdrservice.CdrIngestResponseDto;
import edx.connector.cdrservice.CdrServiceClient;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.core.env.Environment;
import snc.openchargingnetwork.node.components.HttpClientComponent;
import snc.openchargingnetwork.node.models.ocpi.CDR;
import snc.openchargingnetwork.node.models.ocpi.ModuleID;
import snc.openchargingnetwork.node.plugins.core.NodePlugin;
import snc.openchargingnetwork.node.plugins.core.OcpiObjectEvent;
import snc.openchargingnetwork.node.plugins.core.PluginContext;

public final class ConnectorPlugin implements NodePlugin {

    public static final String PLUGIN_ID = "edx";
    public static final String PLUGIN_VERSION = "0.1.0-SNAPSHOT";
    static final int DEFAULT_TIMEOUT_MS = 5_000;

    private static final Logger LOGGER = Logger.getLogger(ConnectorPlugin.class.getName());

    @Override
    public String id() {
        return PLUGIN_ID;
    }

    @Override
    public String version() {
        return PLUGIN_VERSION;
    }

    @Override
    public void init(PluginContext context) {
        Environment environment = context.applicationContext().getEnvironment();
        boolean enabled = readBoolean(environment, "edx.cdr.service.enabled", "EDX_CDR_SERVICE_ENABLED", true);
        if (!enabled) {
            LOGGER.info("EDX CDR service connector is disabled");
            return;
        }

        URI baseUri = URI.create(readRequiredString(
            environment,
            "edx.cdr.service.baseUrl",
            "EDX_CDR_SERVICE_BASE_URL"
        ));
        String apiKey = readString(environment, "edx.cdr.service.apiKey", "EDX_CDR_SERVICE_API_KEY", "");
        int timeoutMs = readInt(environment, "edx.cdr.service.timeoutMs", "EDX_CDR_SERVICE_TIMEOUT_MS", DEFAULT_TIMEOUT_MS);

        if (apiKey.isBlank()) {
            LOGGER.warning("EDX CDR service API key is not configured; requests may be rejected");
        }

        CdrServiceClient cdrServiceClient = new CdrServiceClient(
            baseUri,
            apiKey,
            Duration.ofMillis(timeoutMs),
            cdrObjectMapper(context)
        );
        CdrForwarder forwarder = new CdrForwarder(cdrServiceClient);

        context.ocpiObjectEventRegistry().register(id(), forwarder::forwardIfCdr);
        LOGGER.info(
            "EDX CDR service connector registered; baseUrl=" + baseUri +
                ", apiKeyConfigured=" + !apiKey.isBlank()
        );
    }

    private static String readString(Environment environment, String property, String envName, String defaultValue) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static String readRequiredString(Environment environment, String property, String envName) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration: set " + property + " or " + envName);
        }
        return value.trim();
    }

    private static boolean readBoolean(Environment environment, String property, String envName, boolean defaultValue) {
        return Boolean.parseBoolean(readString(environment, property, envName, Boolean.toString(defaultValue)));
    }

    private static int readInt(Environment environment, String property, String envName, int defaultValue) {
        String value = readString(environment, property, envName, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid " + property + " value '" + value + "', using " + defaultValue);
            return defaultValue;
        }
    }

    private static ObjectMapper cdrObjectMapper(PluginContext context) {
        try {
            return context.applicationContext().getBean(HttpClientComponent.class).getMapper();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to use node ObjectMapper; falling back to default mapper", e);
            return new ObjectMapper();
        }
    }

    static final class CdrForwarder {

        private final CdrServiceClient cdrServiceClient;
        private final ExecutorService executor;

        CdrForwarder(CdrServiceClient cdrServiceClient) {
            this.cdrServiceClient = cdrServiceClient;
            this.executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "edx-cdr-forwarder");
                thread.setDaemon(true);
                return thread;
            });
        }

        void forwardIfCdr(OcpiObjectEvent event) {
            if (event.getModule() != ModuleID.CDRS || !(event.getPayload() instanceof CDR)) {
                return;
            }
            executor.submit(() -> post(event));
        }

        private void post(OcpiObjectEvent event) {
            try {
                CDR cdr = (CDR) event.getPayload();
                CdrIngestResponseDto response = cdrServiceClient.ingestCdr(cdr);
                if (response == null || !response.success()) {
                    LOGGER.warning("EDX CDR ingest failed for CDR " + cdr.getId() + ": " + response);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "EDX CDR ingest failed", e);
            }
        }
    }
}
