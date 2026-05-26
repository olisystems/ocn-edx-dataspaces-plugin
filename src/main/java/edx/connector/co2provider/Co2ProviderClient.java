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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class Co2ProviderClient {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    private final URI publicApiUri;
    private final String authorizationToken;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public Co2ProviderClient(
        URI publicApiUri,
        String authorizationToken,
        Duration timeout,
        ObjectMapper mapper
    ) {
        this(publicApiUri, authorizationToken, timeout, mapper, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    Co2ProviderClient(
        URI publicApiUri,
        String authorizationToken,
        Duration timeout,
        ObjectMapper mapper,
        HttpClient client
    ) {
        this.publicApiUri = publicApiUri;
        this.authorizationToken = authorizationToken;
        this.timeout = timeout;
        this.mapper = mapper;
        this.client = client;
    }

    public Co2EmissionDataResponseDto fetchEmissionData(Co2EmissionQuery query) {
        HttpRequest request = HttpRequest.newBuilder(buildRequestUri(query))
            .timeout(timeout)
            .header("accept", "application/json")
            .header(AUTHORIZATION_HEADER, authorizationToken)
            .GET()
            .build();
        return send(request, Co2EmissionDataResponseDto.class);
    }

    private URI buildRequestUri(Co2EmissionQuery query) {
        String base = publicApiUri.toString();
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(
            base
                + separator
                + "start="
                + encode(query.start())
                + "&end="
                + encode(query.end())
                + "&zone="
                + encode(query.zone())
                + "&time-resolution="
                + encode(query.timeResolution())
                + "&calculation-type="
                + encode(query.calculationType())
                + "&emission-type="
                + encode(query.emissionType())
        );
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        HttpResponse<String> response = sendRaw(request);
        if (response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new Co2ProviderException(
                "Unable to deserialize CO2 provider response",
                response.statusCode(),
                response.body(),
                e
            );
        }
    }

    private HttpResponse<String> sendRaw(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new Co2ProviderException(
                    "CO2 provider request failed",
                    response.statusCode(),
                    response.body()
                );
            }
            return response;
        } catch (IOException e) {
            throw new Co2ProviderException("CO2 provider request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Co2ProviderException("CO2 provider request interrupted", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
