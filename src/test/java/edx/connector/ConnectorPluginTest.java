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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import edx.connector.cdrservice.CdrServiceClient;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import edx.connector.persistence.InMemoryCdrIngestMappingStore;
import org.springframework.core.env.Environment;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import snc.openchargingnetwork.node.models.ocpi.AuthMethod;
import snc.openchargingnetwork.node.models.ocpi.CDR;
import snc.openchargingnetwork.node.models.ocpi.CdrDimension;
import snc.openchargingnetwork.node.models.ocpi.CdrDimensionType;
import snc.openchargingnetwork.node.models.ocpi.CdrLocation;
import snc.openchargingnetwork.node.models.ocpi.CdrToken;
import snc.openchargingnetwork.node.models.ocpi.ChargingPeriod;
import snc.openchargingnetwork.node.models.ocpi.ConnectorFormat;
import snc.openchargingnetwork.node.models.ocpi.ConnectorType;
import snc.openchargingnetwork.node.models.ocpi.GeoLocation;
import snc.openchargingnetwork.node.models.ocpi.InterfaceRole;
import snc.openchargingnetwork.node.models.ocpi.ModuleID;
import snc.openchargingnetwork.node.models.ocpi.PowerType;
import snc.openchargingnetwork.node.models.ocpi.Price;
import snc.openchargingnetwork.node.models.ocpi.TokenType;
import snc.openchargingnetwork.node.plugins.core.OcpiObjectEvent;
import snc.openchargingnetwork.node.plugins.core.OcpiObjectEventPhase;

class ConnectorPluginTest {

    @Test
    void forwarderCanBeConstructed() {
        CdrServiceClient client = new CdrServiceClient(
            URI.create("https://example.invalid"),
            "secret",
            Duration.ofSeconds(5),
            new ObjectMapper()
        );
        CdrForwarder forwarder = new CdrForwarder(client, new InMemoryCdrIngestMappingStore());

        assertNotNull(forwarder);
    }

    @Test
    void cdrServiceBaseUrlIsRequired() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> EdxConnectorAutoConfiguration.readRequiredString(
                emptyEnvironment(),
                "edx.cdr.service.baseUrl",
                "EDX_CDR_SERVICE_BASE_URL_MISSING_TEST"
            )
        );

        assertTrue(error.getMessage().contains("edx.cdr.service.baseUrl"));
    }

    private static Environment emptyEnvironment() {
        return (Environment) Proxy.newProxyInstance(
            Environment.class.getClassLoader(),
            new Class<?>[] { Environment.class },
            (proxy, method, args) -> {
                if ("getProperty".equals(method.getName())) {
                    return null;
                }
                if ("toString".equals(method.getName())) {
                    return "emptyEnvironment";
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    @Test
    void forwarderIngestsCapturedCdr() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<String> apiKeyRef = new AtomicReference<>();
        HttpServer server = startReceiver(received, bodyRef, apiKeyRef);

        try {
            int port = server.getAddress().getPort();
            CdrServiceClient client = new CdrServiceClient(
                URI.create("http://127.0.0.1:" + port),
                "secret",
                Duration.ofSeconds(5),
                new ObjectMapper()
            );
            CdrForwarder forwarder = new CdrForwarder(client, new InMemoryCdrIngestMappingStore());

            forwarder.forwardIfCdr(sampleObjectEvent());

            assertTrue(received.await(5, TimeUnit.SECONDS));
            String body = bodyRef.get();
            assertEquals("secret", apiKeyRef.get());
            assertTrue(body.contains("\"id\":\"cdr-1\""));
            assertTrue(body.contains("\"countryCode\":\"DE\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void forwarderIngestsWhenOcpiStatusIsSuccessEvenIfHttpError() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> bodyRef = new AtomicReference<>();
        HttpServer server = startReceiver(received, bodyRef, new AtomicReference<>());

        try {
            int port = server.getAddress().getPort();
            CdrServiceClient client = new CdrServiceClient(
                URI.create("http://127.0.0.1:" + port),
                "secret",
                Duration.ofSeconds(5),
                new ObjectMapper()
            );
            CdrForwarder forwarder = new CdrForwarder(client, new InMemoryCdrIngestMappingStore());

            forwarder.forwardIfCdr(sampleObjectEvent(500, 1000));

            assertTrue(received.await(5, TimeUnit.SECONDS));
            assertTrue(bodyRef.get().contains("\"id\":\"cdr-1\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void forwarderSkipsWhenOcpiStatusIsNotSuccess() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        HttpServer server = startReceiver(received, new AtomicReference<>(), new AtomicReference<>());

        try {
            int port = server.getAddress().getPort();
            CdrServiceClient client = new CdrServiceClient(
                URI.create("http://127.0.0.1:" + port),
                "secret",
                Duration.ofSeconds(5),
                new ObjectMapper()
            );
            CdrForwarder forwarder = new CdrForwarder(client, new InMemoryCdrIngestMappingStore());

            forwarder.forwardIfCdr(sampleObjectEvent(200, 2000));

            assertFalse(received.await(500, TimeUnit.MILLISECONDS));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void forwarderSkipsWhenOcpiStatusIsMissing() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        HttpServer server = startReceiver(received, new AtomicReference<>(), new AtomicReference<>());

        try {
            int port = server.getAddress().getPort();
            CdrServiceClient client = new CdrServiceClient(
                URI.create("http://127.0.0.1:" + port),
                "secret",
                Duration.ofSeconds(5),
                new ObjectMapper()
            );
            CdrForwarder forwarder = new CdrForwarder(client, new InMemoryCdrIngestMappingStore());

            forwarder.forwardIfCdr(sampleObjectEvent(200, null));

            assertFalse(received.await(500, TimeUnit.MILLISECONDS));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void forwarderStoresMappingAfterSuccessfulIngest() throws Exception {
        forwarderStoresMappingForIngestResponse(
            "{\"success\":true,\"extractionStatus\":\"SUCCESS\",\"rawRecordId\":\"raw-1\"}",
            "raw-1"
        );
    }

    @Test
    void forwarderStoresMappingWhenExtractionFailsButRawRecordIdPresent() throws Exception {
        forwarderStoresMappingForIngestResponse(
            "{\"success\":false,\"extractionStatus\":\"FAILED\",\"rawRecordId\":\"raw-failed-1\"}",
            "raw-failed-1"
        );
    }

    private static void forwarderStoresMappingForIngestResponse(String ingestResponseBody, String expectedServiceId)
        throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        InMemoryCdrIngestMappingStore mappingStore = new InMemoryCdrIngestMappingStore();
        HttpServer server = startReceiver(received, new AtomicReference<>(), new AtomicReference<>(), ingestResponseBody);

        try {
            int port = server.getAddress().getPort();
            CdrServiceClient client = new CdrServiceClient(
                URI.create("http://127.0.0.1:" + port),
                "secret",
                Duration.ofSeconds(5),
                new ObjectMapper()
            );
            CdrForwarder forwarder = new CdrForwarder(client, mappingStore);

            forwarder.forwardIfCdr(sampleObjectEvent());

            assertTrue(received.await(5, TimeUnit.SECONDS));
            forwarder.shutdown();
            assertTrue(mappingStore.find("DE", "CPO", "cdr-1").isPresent());
            assertEquals(expectedServiceId, mappingStore.find("DE", "CPO", "cdr-1").orElseThrow().getServiceId());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startReceiver(
        CountDownLatch received,
        AtomicReference<String> bodyRef,
        AtomicReference<String> apiKeyRef
    ) throws IOException {
        return startReceiver(
            received,
            bodyRef,
            apiKeyRef,
            "{\"success\":true,\"extractionStatus\":\"SUCCESS\",\"rawRecordId\":\"raw-1\"}"
        );
    }

    private static HttpServer startReceiver(
        CountDownLatch received,
        AtomicReference<String> bodyRef,
        AtomicReference<String> apiKeyRef,
        String ingestResponseBody
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/cdr-ingest", exchange -> {
            apiKeyRef.set(exchange.getRequestHeaders().getFirst(CdrServiceClient.API_KEY_HEADER));
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] response = ingestResponseBody.getBytes();
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
            received.countDown();
        });
        server.start();
        return server;
    }

    private static OcpiObjectEvent sampleObjectEvent() {
        return sampleObjectEvent(200, 1000);
    }

    private static OcpiObjectEvent sampleObjectEvent(Integer responseStatusCode, Integer ocpiStatusCode) {
        return new OcpiObjectEvent(
            OcpiObjectEventPhase.REQUEST_BODY,
            ModuleID.CDRS,
            InterfaceRole.RECEIVER,
            HttpMethod.POST,
            null,
            null,
            Map.of(),
            sampleCdr(),
            null,
            "CPO",
            "DE",
            "EMS",
            "FR",
            Map.of("X-Request-ID", "request-1"),
            responseStatusCode,
            ocpiStatusCode
        );
    }

    private static CDR sampleCdr() {
        CdrToken token = new CdrToken("DE", "CPO", "token-1", TokenType.RFID, "DE-CPO-CONTRACT");
        CdrLocation location = new CdrLocation(
            "loc-1",
            null,
            "Main Street",
            "Berlin",
            "10115",
            "DE",
            new GeoLocation("52.5200", "13.4050"),
            "evse-1",
            "DE*CPO*E1",
            "1",
            ConnectorType.IEC_62196_T2,
            ConnectorFormat.SOCKET,
            PowerType.AC_3_PHASE
        );
        ChargingPeriod period = new ChargingPeriod(
            "2026-05-04T00:00:00Z",
            List.of(new CdrDimension(CdrDimensionType.ENERGY, 10.0f)),
            null
        );

        return new CDR(
            "DE",
            "CPO",
            "cdr-1",
            "2026-05-04T00:00:00Z",
            "2026-05-04T01:00:00Z",
            null,
            token,
            AuthMethod.AUTH_REQUEST,
            null,
            location,
            null,
            "EUR",
            null,
            List.of(period),
            null,
            new Price(5.0f, 5.95f),
            null,
            10.0f,
            null,
            1.0f,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "2026-05-04T01:00:00Z",
            null
        );
    }
}
