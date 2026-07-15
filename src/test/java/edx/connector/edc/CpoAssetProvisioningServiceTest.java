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

package edx.connector.edc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import edx.connector.persistence.InMemoryCpoAssetMappingStore;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CpoAssetProvisioningServiceTest {

    @Test
    void provisionsAssetPolicyAndContractDefinitionForCpo() throws Exception {
        AtomicInteger edcRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/policydefinitions", exchange -> {
            edcRequests.incrementAndGet();
            byte[] body = "{\"@id\":\"policy-id\",\"createdAt\":1}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v3/assets", exchange -> {
            edcRequests.incrementAndGet();
            byte[] body = "{\"@id\":\"asset-id\",\"createdAt\":1}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v3/contractdefinitions", exchange -> {
            edcRequests.incrementAndGet();
            byte[] body = "{\"@id\":\"contract-def-id\",\"createdAt\":1}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            InMemoryCpoAssetMappingStore store = new InMemoryCpoAssetMappingStore();
            CpoAssetProvisioningService service = new CpoAssetProvisioningService(
                new EdcManagementClient(
                    URI.create("http://127.0.0.1:" + port),
                    "",
                    Duration.ofSeconds(5),
                    new ObjectMapper()
                ),
                store,
                new ObjectMapper(),
                new EdcAssetSettings(
                    "cdr-data",
                    "https://cdr.example.com/api/v1/co2-relevant-cdr",
                    "secret",
                    List.of()
                )
            );

            service.ensureForCpo("de", "cpo");
            service.ensureForCpo("de", "cpo");

            assertEquals(4, edcRequests.get());
            assertTrue(store.find("DE", "CPO").isPresent());
            assertEquals("cdr-data:src:DE-CPO:tgt:", store.find("DE", "CPO").orElseThrow().getAssetId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void adoptsConsumersFromPreExistingEdcPolicyInsteadOfDefaults() throws Exception {
        String existingPolicy = """
            {
              "@id": "policy-access-DE-CPO",
              "policy": {
                "@type": "Set",
                "permission": [{
                  "action": "use",
                  "constraint": {
                    "leftOperand": "MarketPartner.mpId",
                    "operator": "eq",
                    "rightOperand": "4045399000008"
                  }
                }]
              }
            }
            """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/policydefinitions", exchange -> {
            byte[] body;
            int status;
            if ("GET".equals(exchange.getRequestMethod())) {
                body = existingPolicy.getBytes();
                status = 200;
            } else {
                body = "{}".getBytes();
                status = 409;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v3/assets", exchange -> {
            byte[] body = "{}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(409, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v3/contractdefinitions", exchange -> {
            byte[] body = "{}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(409, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            InMemoryCpoAssetMappingStore store = new InMemoryCpoAssetMappingStore();
            CpoAssetProvisioningService service = new CpoAssetProvisioningService(
                new EdcManagementClient(
                    URI.create("http://127.0.0.1:" + port),
                    "",
                    Duration.ofSeconds(5),
                    new ObjectMapper()
                ),
                store,
                new ObjectMapper(),
                new EdcAssetSettings(
                    "cdr-data",
                    "https://cdr.example.com/api/v1/co2-relevant-cdr",
                    "secret",
                    List.of(new PolicyConsumerSubject(PolicyConsumerSubjectType.DID, "did:web:example:default"))
                )
            );

            service.ensureForCpo("DE", "CPO");

            String consumersJson = store.find("DE", "CPO").orElseThrow().getAllowedConsumersJson();
            assertTrue(consumersJson.contains("4045399000008"));
            assertFalse(consumersJson.contains("did:web:example:default"));
        } finally {
            server.stop(0);
        }
    }
}
