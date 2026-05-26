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

package edx.connector.enrichment;

import edx.connector.cdrservice.CdrServiceClient;
import edx.connector.cdrservice.RawCdrDto;
import edx.connector.co2provider.CdrCo2Enricher;
import edx.connector.co2provider.Co2EmissionQuery;
import edx.connector.co2provider.Co2EnrichmentDefaults;
import edx.connector.co2provider.Co2ProviderClient;
import edx.connector.persistence.CdrIngestMappingStore;
import edx.connector.persistence.EdxCdrIngestMapping;
import java.util.Map;
import java.util.Optional;

public final class CdrCo2EnrichmentService {

    private final CdrIngestMappingStore mappingStore;
    private final CdrServiceClient cdrServiceClient;
    private final Co2ProviderClient co2ProviderClient;
    private final Co2EnrichmentDefaults defaults;
    private final CdrCo2Enricher enricher = new CdrCo2Enricher();

    public CdrCo2EnrichmentService(
        CdrIngestMappingStore mappingStore,
        CdrServiceClient cdrServiceClient,
        Co2ProviderClient co2ProviderClient,
        Co2EnrichmentDefaults defaults
    ) {
        this.mappingStore = mappingStore;
        this.cdrServiceClient = cdrServiceClient;
        this.co2ProviderClient = co2ProviderClient;
        this.defaults = defaults == null ? Co2EnrichmentDefaults.standard() : defaults;
    }

    public Optional<EnrichedCdrDto> enrich(String countryCode, String partyId, String cdrId, String zone) {
        return enrich(countryCode, partyId, cdrId, zone, null);
    }

    public Optional<EnrichedCdrDto> enrich(
        String countryCode,
        String partyId,
        String cdrId,
        String zone,
        Co2EmissionQuery queryOverrides
    ) {
        if (!hasText(countryCode) || !hasText(partyId) || !hasText(cdrId) || !hasText(zone)) {
            return Optional.empty();
        }
        return mappingStore.find(countryCode, partyId, cdrId)
            .flatMap(mapping -> enrichRawRecord(mapping, zone, queryOverrides));
    }

    private Optional<EnrichedCdrDto> enrichRawRecord(
        EdxCdrIngestMapping mapping,
        String zone,
        Co2EmissionQuery queryOverrides
    ) {
        RawCdrDto raw = cdrServiceClient.getRawCdrById(mapping.getServiceId());
        if (raw == null || raw.cdr() == null || raw.cdr().isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> sourceCdr = raw.cdr();
        String sessionStart = CdrCo2Enricher.chargingWindowStart(sourceCdr);
        String sessionEnd = CdrCo2Enricher.chargingWindowEnd(sourceCdr);
        if (!hasText(sessionStart) || !hasText(sessionEnd)) {
            return Optional.empty();
        }
        String start = CdrCo2Enricher.co2QueryWindowStart(sessionStart);
        String end = CdrCo2Enricher.co2QueryWindowEnd(sessionEnd);
        if (!hasText(start) || !hasText(end)) {
            return Optional.empty();
        }

        Co2EmissionQuery query = queryOverrides != null
            ? queryOverrides
            : new Co2EmissionQuery(
                start,
                end,
                zone,
                defaults.timeResolution(),
                defaults.calculationType(),
                defaults.emissionType()
            );

        Map<String, Object> enrichedCdr = enricher.enrich(
            sourceCdr,
            co2ProviderClient.fetchEmissionData(query),
            zone
        );

        return Optional.of(new EnrichedCdrDto(
            mapping.getCountryCode(),
            mapping.getPartyId(),
            mapping.getCdrId(),
            mapping.getServiceId(),
            raw.receivedAt(),
            enrichedCdr
        ));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
