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

package edx.connector.co2provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class Co2ProviderClientTest {

    private static final String SAMPLE_RESPONSE =
        """
        {
            "startUtc": "2025-02-18T09:00:00Z",
            "stopUtc": "2025-02-18T18:00:00Z",
            "measurements": [
                {
                    "unit": "gCO2eqPerkWh",
                    "zone": "DE_LU",
                    "timeResolution": "Hourly",
                    "calculationType": "Consumption",
                    "emissionType": "Lifecycle",
                    "measurementValues": [
                        {
                            "timestamp": "2025-02-18T09:00:00Z",
                            "calculatedAt": "2026-01-31T10:20:34Z",
                            "value": 419.7681,
                            "valueStatus": "Actual"
                        },
                        {
                            "timestamp": "2025-02-18T10:00:00Z",
                            "calculatedAt": "2026-01-31T10:21:13Z",
                            "value": 387.6565,
                            "valueStatus": "Actual"
                        }
                    ]
                }
            ]
        }
        """;

    @Test
    void fetchEmissionDataCallsProviderAndDeserializesResponse() throws Exception {
        AtomicReference<String> authRef = new AtomicReference<>();
        AtomicReference<String> queryRef = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authRef.set(exchange.getRequestHeaders().getFirst(Co2ProviderClient.AUTHORIZATION_HEADER));
            queryRef.set(exchange.getRequestURI().getRawQuery());
            byte[] response = SAMPLE_RESPONSE.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            Co2ProviderClient client =
                new Co2ProviderClient(
                    URI.create("http://127.0.0.1:" + port),
                    "co2-token",
                    Duration.ofSeconds(5),
                    new ObjectMapper()
                );
            Co2EmissionDataResponseDto body =
                client.fetchEmissionData(
                    new Co2EmissionQuery(
                        "2025-02-18T09:00:00Z",
                        "2025-02-18T18:00:00Z",
                        "DE_LU",
                        "Hourly",
                        "Consumption",
                        "Lifecycle"
                    )
                );

            assertEquals("co2-token", authRef.get());
            assertTrue(queryRef.get().contains("start=2025-02-18T09%3A00%3A00Z"));
            assertTrue(queryRef.get().contains("zone=DE_LU"));
            assertTrue(queryRef.get().contains("time-resolution=Hourly"));
            assertEquals("2025-02-18T09:00:00Z", body.startUtc());
            assertEquals("2025-02-18T18:00:00Z", body.stopUtc());
            assertEquals(1, body.measurements().size());
            assertEquals("gCO2eqPerkWh", body.measurements().get(0).unit());
            assertEquals(2, body.measurements().get(0).measurementValues().size());
            assertEquals(419.7681, body.measurements().get(0).measurementValues().get(0).value());
            assertEquals("Actual", body.measurements().get(0).measurementValues().get(0).valueStatus());
        } finally {
            server.stop(0);
        }
    }
}
