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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import edx.connector.cdrservice.CdrServiceClient;
import edx.connector.cdrservice.IngestedCdrLookup;
import edx.connector.cdrservice.ResolvedIngestedCdrDto;
import edx.connector.persistence.InMemoryCdrIngestMappingStore;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class EdxIngestedCdrControllerTest {

    @Test
    void resolveIngestedCdrReturnsRawCdr() throws Exception {
        String rawBody =
            """
            [
              {
                "id": "raw-1",
                "receivedAt": "2026-05-04T01:00:00Z",
                "cdr": {
                  "country_code": "DE",
                  "party_id": "ABC",
                  "id": "cdr-lab",
                  "total_energy": 10.0
                }
              }
            ]
            """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/raw-cdr", exchange -> writeJson(exchange, rawBody));
        server.start();

        try {
            int port = server.getAddress().getPort();
            CdrServiceClient client = new CdrServiceClient(
                URI.create("http://127.0.0.1:" + port),
                "secret",
                Duration.ofSeconds(5),
                new ObjectMapper()
            );
            EdxIngestedCdrController controller = new EdxIngestedCdrController(
                client,
                new IngestedCdrLookup(client, new InMemoryCdrIngestMappingStore())
            );

            ResponseEntity<ResolvedIngestedCdrDto> response = controller.resolveIngestedCdr(
                null,
                null,
                "cdr-lab",
                null
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("raw-1", response.getBody().serviceId());
            assertEquals(10.0, response.getBody().cdr().get("total_energy"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveIngestedCdrReturnsNotFoundWhenMissing() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/raw-cdr", exchange -> writeJson(exchange, "[]"));
        server.start();

        try {
            int port = server.getAddress().getPort();
            CdrServiceClient client = new CdrServiceClient(
                URI.create("http://127.0.0.1:" + port),
                "secret",
                Duration.ofSeconds(5),
                new ObjectMapper()
            );
            EdxIngestedCdrController controller = new EdxIngestedCdrController(
                client,
                new IngestedCdrLookup(client, new InMemoryCdrIngestMappingStore())
            );

            ResponseEntity<ResolvedIngestedCdrDto> response = controller.resolveIngestedCdr(
                null,
                null,
                "missing",
                null
            );

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertTrue(response.getBody() == null);
        } finally {
            server.stop(0);
        }
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] response = body.getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
