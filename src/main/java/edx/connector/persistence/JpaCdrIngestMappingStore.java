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

package edx.connector.persistence;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaCdrIngestMappingStore implements CdrIngestMappingStore {

    private final EdxCdrIngestMappingRepository repository;

    public JpaCdrIngestMappingStore(EdxCdrIngestMappingRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void recordSuccessfulIngest(String countryCode, String partyId, String cdrId, String serviceId) {
        String normalizedCountry = normalizeCountryCode(countryCode);
        String normalizedParty = normalizePartyId(partyId);
        String normalizedCdrId = normalizeCdrId(cdrId);
        String normalizedServiceId = normalizeServiceId(serviceId);

        repository.findByCountryCodeAndPartyIdAndCdrId(normalizedCountry, normalizedParty, normalizedCdrId)
            .ifPresentOrElse(
                existing -> {
                    existing.setServiceId(normalizedServiceId);
                    repository.save(existing);
                },
                () -> repository.save(new EdxCdrIngestMapping(
                    normalizedCountry,
                    normalizedParty,
                    normalizedCdrId,
                    normalizedServiceId
                ))
            );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EdxCdrIngestMapping> find(String countryCode, String partyId, String cdrId) {
        if (isBlank(countryCode) || isBlank(partyId) || isBlank(cdrId)) {
            return Optional.empty();
        }
        return repository.findByCountryCodeAndPartyIdAndCdrId(
            normalizeCountryCode(countryCode),
            normalizePartyId(partyId),
            normalizeCdrId(cdrId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EdxCdrIngestMapping> findByServiceId(String serviceId) {
        if (isBlank(serviceId)) {
            return Optional.empty();
        }
        return repository.findByServiceId(normalizeServiceId(serviceId));
    }

    static String normalizeCountryCode(String countryCode) {
        return countryCode.trim().toUpperCase();
    }

    static String normalizePartyId(String partyId) {
        return partyId.trim().toUpperCase();
    }

    static String normalizeCdrId(String cdrId) {
        return cdrId.trim();
    }

    static String normalizeServiceId(String serviceId) {
        return serviceId.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
