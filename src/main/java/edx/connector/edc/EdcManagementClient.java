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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class EdcManagementClient {

    static final String V3_ASSETS = "/v3/assets";
    static final String V3_POLICY_DEFINITIONS = "/v3/policydefinitions";
    static final String V3_CONTRACT_DEFINITIONS = "/v3/contractdefinitions";

    private final URI baseUri;
    private final String apiKey;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public EdcManagementClient(URI baseUri, String apiKey, Duration timeout, ObjectMapper mapper) {
        this(baseUri, apiKey, timeout, mapper, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    EdcManagementClient(URI baseUri, String apiKey, Duration timeout, ObjectMapper mapper, HttpClient client) {
        this.baseUri = baseUri;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.mapper = mapper;
        this.client = client;
    }

    public EdcIdResponseDto createAsset(Map<String, Object> asset) {
        return post(V3_ASSETS, asset, EdcIdResponseDto.class);
    }

    public void updateAsset(Map<String, Object> asset) {
        String assetId = readResourceId(asset, "asset");
        put(V3_ASSETS + "/" + assetId, asset);
    }

    public EdcIdResponseDto createPolicyDefinition(Map<String, Object> policyDefinition) {
        return post(V3_POLICY_DEFINITIONS, policyDefinition, EdcIdResponseDto.class);
    }

    public void updatePolicyDefinition(Map<String, Object> policyDefinition) {
        String policyId = readResourceId(policyDefinition, "policy definition");
        put(V3_POLICY_DEFINITIONS + "/" + policyId, policyDefinition);
    }

    public EdcIdResponseDto createContractDefinition(Map<String, Object> contractDefinition) {
        return post(V3_CONTRACT_DEFINITIONS, contractDefinition, EdcIdResponseDto.class);
    }

    private <T> T post(String apiPath, Object body, Class<T> responseType) {
        try {
            HttpRequest request = requestBuilder(apiPath)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            return send(request, responseType);
        } catch (IOException e) {
            throw new EdcManagementException("Unable to serialize EDC management request", e);
        }
    }

    private void put(String apiPath, Object body) {
        try {
            HttpRequest request = requestBuilder(apiPath)
                .header("content-type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            sendRaw(request);
        } catch (IOException e) {
            throw new EdcManagementException("Unable to serialize EDC management request", e);
        }
    }

    private HttpRequest.Builder requestBuilder(String apiPath) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(apiPath))
            .timeout(timeout)
            .header("accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("x-api-key", apiKey);
        }
        return builder;
    }

    private URI endpoint(String apiPath) {
        String base = baseUri.toString();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (apiPath.startsWith("/") && base.endsWith("/api/management")) {
            return URI.create(base + apiPath);
        }
        return URI.create(base + (apiPath.startsWith("/") ? apiPath : "/" + apiPath));
    }

    private static String readResourceId(Map<String, Object> body, String resourceType) {
        Object id = body.get("@id");
        if (id == null || id.toString().isBlank()) {
            throw new EdcManagementException("EDC " + resourceType + " @id is required for update", 400, "");
        }
        return id.toString();
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        HttpResponse<String> response = sendRaw(request);
        if (response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new EdcManagementException(
                "Unable to deserialize EDC management response",
                response.statusCode(),
                response.body(),
                e
            );
        }
    }

    private HttpResponse<String> sendRaw(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EdcManagementException("EDC management request failed", response.statusCode(), response.body());
            }
            return response;
        } catch (IOException e) {
            throw new EdcManagementException("EDC management request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EdcManagementException("EDC management request interrupted", e);
        }
    }
}
