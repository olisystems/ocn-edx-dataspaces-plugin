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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryCdrIngestMappingStore implements CdrIngestMappingStore {

    private final Map<String, EdxCdrIngestMapping> byOcpiIdentity = new HashMap<>();
    private final Map<String, EdxCdrIngestMapping> byServiceId = new HashMap<>();

    @Override
    public void recordSuccessfulIngest(String countryCode, String partyId, String cdrId, String serviceId) {
        String country = JpaCdrIngestMappingStore.normalizeCountryCode(countryCode);
        String party = JpaCdrIngestMappingStore.normalizePartyId(partyId);
        String cdr = JpaCdrIngestMappingStore.normalizeCdrId(cdrId);
        String service = JpaCdrIngestMappingStore.normalizeServiceId(serviceId);
        String key = ocpiKey(country, party, cdr);

        EdxCdrIngestMapping mapping = byOcpiIdentity.get(key);
        if (mapping == null) {
            mapping = new EdxCdrIngestMapping(country, party, cdr, service);
            mapping.onCreate();
            byOcpiIdentity.put(key, mapping);
        } else {
            byServiceId.remove(mapping.getServiceId());
            mapping.setServiceId(service);
            mapping.onUpdate();
        }
        byServiceId.put(service, mapping);
    }

    @Override
    public Optional<EdxCdrIngestMapping> find(String countryCode, String partyId, String cdrId) {
        if (countryCode == null || countryCode.isBlank()
            || partyId == null || partyId.isBlank()
            || cdrId == null || cdrId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byOcpiIdentity.get(ocpiKey(
            JpaCdrIngestMappingStore.normalizeCountryCode(countryCode),
            JpaCdrIngestMappingStore.normalizePartyId(partyId),
            JpaCdrIngestMappingStore.normalizeCdrId(cdrId)
        )));
    }

    @Override
    public Optional<EdxCdrIngestMapping> findByServiceId(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byServiceId.get(JpaCdrIngestMappingStore.normalizeServiceId(serviceId)));
    }

    private static String ocpiKey(String countryCode, String partyId, String cdrId) {
        return countryCode + "|" + partyId + "|" + cdrId;
    }
}
