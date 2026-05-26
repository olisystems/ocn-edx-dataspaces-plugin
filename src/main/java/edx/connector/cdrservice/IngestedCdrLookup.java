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

import edx.connector.persistence.CdrIngestMappingStore;
import edx.connector.persistence.EdxCdrIngestMapping;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class IngestedCdrLookup {

    private final CdrServiceClient cdrServiceClient;
    private final CdrIngestMappingStore mappingStore;

    public IngestedCdrLookup(CdrServiceClient cdrServiceClient, CdrIngestMappingStore mappingStore) {
        this.cdrServiceClient = cdrServiceClient;
        this.mappingStore = mappingStore;
    }

    public Optional<ResolvedIngestedCdrDto> resolve(String countryCode, String partyId, String cdrId) {
        if (hasText(countryCode) && hasText(partyId) && hasText(cdrId)) {
            Optional<ResolvedIngestedCdrDto> mapped = mappingStore.find(countryCode, partyId, cdrId)
                .flatMap(this::toResolved);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        if (hasText(cdrId)) {
            return latestRawMatching(cdrServiceClient.getAllRawCdrs(), cdrId.trim())
                .flatMap(this::toResolvedFromRaw);
        }
        return resolveLatest();
    }

    public Optional<ResolvedIngestedCdrDto> resolveLatest() {
        return cdrServiceClient.getAllRawCdrs().stream()
            .max(Comparator.comparing(RawCdrDto::receivedAt, Comparator.nullsLast(String::compareTo)))
            .flatMap(this::toResolvedFromRaw);
    }

    private Optional<ResolvedIngestedCdrDto> toResolved(EdxCdrIngestMapping mapping) {
        RawCdrDto raw = cdrServiceClient.getRawCdrById(mapping.getServiceId());
        if (raw == null || raw.cdr() == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedIngestedCdrDto(
            mapping.getCountryCode(),
            mapping.getPartyId(),
            mapping.getCdrId(),
            mapping.getServiceId(),
            raw.receivedAt(),
            raw.cdr()
        ));
    }

    private Optional<ResolvedIngestedCdrDto> toResolvedFromRaw(RawCdrDto raw) {
        if (raw == null || raw.cdr() == null) {
            return Optional.empty();
        }
        Map<String, Object> cdr = raw.cdr();
        return Optional.of(new ResolvedIngestedCdrDto(
            stringValue(cdr.get("country_code")),
            stringValue(cdr.get("party_id")),
            stringValue(cdr.get("id")),
            raw.id(),
            raw.receivedAt(),
            cdr
        ));
    }

    static Optional<RawCdrDto> latestRawMatching(List<RawCdrDto> records, String cdrId) {
        return records.stream()
            .filter(record -> cdrId.equalsIgnoreCase(ocpiIdFromRaw(record)))
            .max(Comparator.comparing(RawCdrDto::receivedAt, Comparator.nullsLast(String::compareTo)));
    }

    static String ocpiIdFromRaw(RawCdrDto raw) {
        if (raw == null || raw.cdr() == null) {
            return null;
        }
        return stringValue(raw.cdr().get("id"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, null);
        return text == null || text.isBlank() ? null : text;
    }
}
