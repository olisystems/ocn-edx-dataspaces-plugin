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
import org.springframework.stereotype.Repository;

@Repository
public class JpaCpoAssetMappingStore implements CpoAssetMappingStore {

    private final EdxCpoAssetMappingRepository repository;

    public JpaCpoAssetMappingStore(EdxCpoAssetMappingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<EdxCpoAssetMapping> find(String countryCode, String partyId) {
        if (!hasText(countryCode) || !hasText(partyId)) {
            return Optional.empty();
        }
        return repository.findByCountryCodeAndPartyId(
            normalizeCountryCode(countryCode),
            normalizePartyId(partyId)
        );
    }

    @Override
    public EdxCpoAssetMapping save(EdxCpoAssetMapping mapping) {
        return repository.save(mapping);
    }

    @Override
    public EdxCpoAssetMapping updateAllowedConsumers(EdxCpoAssetMapping mapping, String allowedConsumersJson) {
        mapping.setAllowedConsumersJson(allowedConsumersJson);
        return repository.save(mapping);
    }

    public static String normalizeCountryCode(String countryCode) {
        return countryCode.trim().toUpperCase();
    }

    public static String normalizePartyId(String partyId) {
        return partyId.trim().toUpperCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
