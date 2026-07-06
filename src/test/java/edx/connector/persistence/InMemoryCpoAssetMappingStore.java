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

public final class InMemoryCpoAssetMappingStore implements CpoAssetMappingStore {

    private final Map<String, EdxCpoAssetMapping> byKey = new HashMap<>();

    @Override
    public Optional<EdxCpoAssetMapping> find(String countryCode, String partyId) {
        return Optional.ofNullable(byKey.get(key(countryCode, partyId)));
    }

    @Override
    public EdxCpoAssetMapping save(EdxCpoAssetMapping mapping) {
        byKey.put(key(mapping.getCountryCode(), mapping.getPartyId()), mapping);
        return mapping;
    }

    @Override
    public EdxCpoAssetMapping updateAllowedConsumers(EdxCpoAssetMapping mapping, String allowedConsumersJson) {
        mapping.setAllowedConsumersJson(allowedConsumersJson);
        return save(mapping);
    }

    private static String key(String countryCode, String partyId) {
        return JpaCpoAssetMappingStore.normalizeCountryCode(countryCode)
            + "/"
            + JpaCpoAssetMappingStore.normalizePartyId(partyId);
    }
}
