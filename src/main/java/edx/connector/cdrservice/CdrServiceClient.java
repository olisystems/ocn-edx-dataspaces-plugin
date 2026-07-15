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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class CdrServiceClient {

    public static final String API_KEY_HEADER = "x-api-key";
    public static final String SOURCE_HEADER = "x-source";
    public static final String TARGET_HEADER = "x-target";

    private final URI baseUri;
    private final String apiKey;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public CdrServiceClient(URI baseUri, String apiKey, Duration timeout, ObjectMapper mapper) {
        this(baseUri, apiKey, timeout, mapper, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    CdrServiceClient(URI baseUri, String apiKey, Duration timeout, ObjectMapper mapper, HttpClient client) {
        this.baseUri = baseUri;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.mapper = mapper;
        this.client = client;
    }

    public CdrIngestResponseDto ingestCdr(CdrIngestRequestDto ingestRequest) {
        try {
            HttpRequest request = requestBuilder(CdrServicePaths.CDR_INGEST)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(ingestRequest)))
                .build();
            return send(request, CdrIngestResponseDto.class);
        } catch (IOException e) {
            throw new CdrServiceException("Unable to serialize CDR ingest request", e);
        }
    }

    public List<RawCdrDto> getAllRawCdrs() {
        HttpRequest request = requestBuilder(CdrServicePaths.RAW_CDR).GET().build();
        return send(request, new TypeReference<>() {});
    }

    public RawCdrDto getRawCdrById(String id) {
        HttpRequest request = requestBuilder(CdrServicePaths.RAW_CDR + "/" + pathSegment(id)).GET().build();
        return send(request, RawCdrDto.class);
    }

    public void deleteRawCdrById(String id) {
        HttpRequest request = requestBuilder(CdrServicePaths.RAW_CDR + "/" + pathSegment(id)).DELETE().build();
        send(request);
    }

    public PaginatedCo2RelevantCdrResponseDto getAllCo2RelevantCdrs(
        Co2RelevantCdrQuery query,
        CdrRoutingHeaders routing
    ) {
        HttpRequest request = withRoutingHeaders(
            requestBuilder(CdrServicePaths.CO2_RELEVANT_CDR + co2RelevantQuery(query)).GET(),
            routing
        ).build();
        return send(request, PaginatedCo2RelevantCdrResponseDto.class);
    }

    public Co2RelevantCdrResponseDto getCo2RelevantCdrById(String id, CdrRoutingHeaders routing) {
        HttpRequest request = withRoutingHeaders(
            requestBuilder(CdrServicePaths.CO2_RELEVANT_CDR + "/" + pathSegment(id)).GET(),
            routing
        ).build();
        return send(request, Co2RelevantCdrResponseDto.class);
    }

    public RawCdrDto getRawCdrByCo2RelevantCdrId(String id, CdrRoutingHeaders routing) {
        HttpRequest request = withRoutingHeaders(
            requestBuilder(CdrServicePaths.CO2_RELEVANT_CDR + "/" + pathSegment(id) + "/raw").GET(),
            routing
        ).build();
        return send(request, RawCdrDto.class);
    }

    private HttpRequest.Builder requestBuilder(String apiPath) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(apiPath))
            .timeout(timeout)
            .header("accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header(API_KEY_HEADER, apiKey);
        }
        return builder;
    }

    private HttpRequest.Builder withRoutingHeaders(HttpRequest.Builder builder, CdrRoutingHeaders routing) {
        if (routing == null) {
            return builder;
        }
        if (routing.source() != null && !routing.source().isBlank()) {
            builder.header(SOURCE_HEADER, routing.source());
        }
        if (routing.target() != null && !routing.target().isBlank()) {
            builder.header(TARGET_HEADER, routing.target());
        }
        return builder;
    }

    private String co2RelevantQuery(Co2RelevantCdrQuery query) {
        if (query == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendQueryParam(builder, "page", query.page());
        appendQueryParam(builder, "limit", query.limit());
        appendQueryParam(builder, "sortBy", query.sortBy());
        appendQueryParam(builder, "sortOrder", query.sortOrder());
        return builder.toString();
    }

    private void appendQueryParam(StringBuilder builder, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        builder.append(builder.length() == 0 ? "?" : "&");
        builder.append(name);
        builder.append("=");
        builder.append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
    }

    private URI endpoint(String apiPath) {
        String base = baseUri.toString();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = base.endsWith("/api") && apiPath.startsWith("/api/")
            ? apiPath.substring("/api".length())
            : apiPath;
        return URI.create(base + path);
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        HttpResponse<String> response = sendRaw(request);
        if (response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new CdrServiceException("Unable to deserialize CDR service response", response.statusCode(), response.body(), e);
        }
    }

    private <T> T send(HttpRequest request, TypeReference<T> responseType) {
        HttpResponse<String> response = sendRaw(request);
        if (response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new CdrServiceException("Unable to deserialize CDR service response", response.statusCode(), response.body(), e);
        }
    }

    private void send(HttpRequest request) {
        sendRaw(request);
    }

    private HttpResponse<String> sendRaw(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CdrServiceException("CDR service request failed", response.statusCode(), response.body());
            }
            return response;
        } catch (IOException e) {
            throw new CdrServiceException("CDR service request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CdrServiceException("CDR service request interrupted", e);
        }
    }

    private String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
