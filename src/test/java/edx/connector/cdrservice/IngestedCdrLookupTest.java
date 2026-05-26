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

package edx.connector.cdrservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import edx.connector.persistence.InMemoryCdrIngestMappingStore;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IngestedCdrLookupTest {

    @Test
    void resolveUsesStoredServiceId() throws Exception {
        String rawBody =
            """
            {
              "id": "service-1",
              "receivedAt": "2026-02-18T09:00:00Z",
              "cdr": {
                "country_code": "DE",
                "party_id": "ABC",
                "id": "cdr-lab",
                "total_energy": 8.5
              }
            }
            """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/raw-cdr/service-1", exchange -> writeJson(exchange, rawBody));
        server.createContext("/api/raw-cdr", exchange -> writeJson(exchange, "[]"));
        server.start();

        try {
            InMemoryCdrIngestMappingStore mappingStore = new InMemoryCdrIngestMappingStore();
            mappingStore.recordSuccessfulIngest("DE", "ABC", "cdr-lab", "service-1");
            IngestedCdrLookup lookup = lookup(server, mappingStore);
            ResolvedIngestedCdrDto resolved = lookup.resolve("DE", "ABC", "cdr-lab").orElseThrow();
            assertEquals("service-1", resolved.serviceId());
            assertEquals("cdr-lab", resolved.cdrId());
            assertEquals(8.5, resolved.cdr().get("total_energy"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveByCdrIdFallsBackToLatestRawMatch() throws Exception {
        String rawBody =
            """
            [
              {
                "id": "raw-1",
                "receivedAt": "2026-02-18T09:00:00Z",
                "cdr": { "id": "cdr-lab", "total_energy": 8.5 }
              }
            ]
            """;
        HttpServer server = startServer(rawBody);
        try {
            IngestedCdrLookup lookup = lookup(server);
            ResolvedIngestedCdrDto resolved = lookup.resolve(null, null, "cdr-lab").orElseThrow();
            assertEquals("raw-1", resolved.serviceId());
            assertEquals(8.5, resolved.cdr().get("total_energy"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveReturnsEmptyWhenNoMatch() throws Exception {
        HttpServer server = startServer("[]");
        try {
            IngestedCdrLookup lookup = lookup(server);
            Optional<ResolvedIngestedCdrDto> resolved = lookup.resolve(null, null, "missing");
            assertTrue(resolved.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startServer(String rawBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/raw-cdr", exchange -> writeJson(exchange, rawBody));
        server.start();
        return server;
    }

    private static IngestedCdrLookup lookup(HttpServer server, InMemoryCdrIngestMappingStore mappingStore) {
        int port = server.getAddress().getPort();
        CdrServiceClient client = new CdrServiceClient(
            URI.create("http://127.0.0.1:" + port),
            "secret",
            Duration.ofSeconds(5),
            new ObjectMapper()
        );
        return new IngestedCdrLookup(client, mappingStore);
    }

    private static IngestedCdrLookup lookup(HttpServer server) {
        return lookup(server, new InMemoryCdrIngestMappingStore());
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] response = body.getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
