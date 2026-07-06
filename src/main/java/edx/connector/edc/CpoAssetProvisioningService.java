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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edx.connector.persistence.CpoAssetMappingStore;
import edx.connector.persistence.EdxCpoAssetMapping;
import edx.connector.persistence.JpaCpoAssetMappingStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CpoAssetProvisioningService {

    private static final Logger LOGGER = Logger.getLogger(CpoAssetProvisioningService.class.getName());

    private final EdcManagementClient edcManagementClient;
    private final CpoAssetMappingStore mappingStore;
    private final ObjectMapper mapper;
    private final EdcAssetSettings settings;

    public CpoAssetProvisioningService(
        EdcManagementClient edcManagementClient,
        CpoAssetMappingStore mappingStore,
        ObjectMapper mapper,
        EdcAssetSettings settings
    ) {
        this.edcManagementClient = edcManagementClient;
        this.mappingStore = mappingStore;
        this.mapper = mapper;
        this.settings = settings;
    }

    public void ensureForCpo(String countryCode, String partyId) {
        if (!hasText(countryCode) || !hasText(partyId)) {
            return;
        }
        String normalizedCountry = JpaCpoAssetMappingStore.normalizeCountryCode(countryCode);
        String normalizedParty = JpaCpoAssetMappingStore.normalizePartyId(partyId);
        Optional<EdxCpoAssetMapping> existing = mappingStore.find(normalizedCountry, normalizedParty);
        if (existing.isPresent()) {
            return;
        }

        String sourceKey = CpoAssetIds.sourceKey(normalizedCountry, normalizedParty);
        String assetId = CpoAssetIds.assetId(settings.assetPrefix(), sourceKey);
        String accessPolicyId = CpoAssetIds.accessPolicyId(sourceKey);
        String contractPolicyId = CpoAssetIds.contractPolicyId(sourceKey);
        String contractDefinitionId = CpoAssetIds.contractDefinitionId(sourceKey);
        List<PolicyConsumerSubject> initialConsumers = settings.defaultConsumers();

        try {
            createIfNeeded(
                () -> edcManagementClient.createPolicyDefinition(
                    CpoPolicyBuilder.buildPolicyDefinition(accessPolicyId, initialConsumers)
                ),
                "access policy",
                accessPolicyId
            );
            createIfNeeded(
                () -> edcManagementClient.createPolicyDefinition(
                    CpoPolicyBuilder.buildPolicyDefinition(contractPolicyId, initialConsumers)
                ),
                "contract policy",
                contractPolicyId
            );
            createIfNeeded(
                () -> edcManagementClient.createAsset(
                    CpoAssetFactory.buildAsset(
                        assetId,
                        sourceKey,
                        settings.co2RelevantCdrUrl(),
                        settings.cdrServiceApiKey()
                    )
                ),
                "asset",
                assetId
            );
            createIfNeeded(
                () -> edcManagementClient.createContractDefinition(
                    CpoAssetFactory.buildContractDefinition(
                        contractDefinitionId,
                        assetId,
                        accessPolicyId,
                        contractPolicyId
                    )
                ),
                "contract definition",
                contractDefinitionId
            );

            mappingStore.save(new EdxCpoAssetMapping(
                normalizedCountry,
                normalizedParty,
                sourceKey,
                assetId,
                accessPolicyId,
                contractPolicyId,
                contractDefinitionId,
                serializeConsumers(initialConsumers)
            ));
            LOGGER.info(
                "Provisioned EDX dataspace asset for CPO "
                    + normalizedCountry + "/" + normalizedParty
                    + " -> assetId=" + assetId
            );
        } catch (Exception e) {
            LOGGER.log(
                Level.WARNING,
                "Failed to provision EDX dataspace asset for CPO " + normalizedCountry + "/" + normalizedParty,
                e
            );
        }
    }

    private void createIfNeeded(EdcCreateAction action, String resourceType, String resourceId) {
        try {
            action.run();
        } catch (EdcManagementException e) {
            if (e.statusCode() == 409) {
                LOGGER.info("EDC " + resourceType + " already exists: " + resourceId);
                return;
            }
            throw e;
        }
    }

    private String serializeConsumers(List<PolicyConsumerSubject> consumers) {
        try {
            return mapper.writeValueAsString(consumers == null ? List.of() : consumers);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize policy consumers", e);
        }
    }

    List<PolicyConsumerSubject> deserializeConsumers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to deserialize stored policy consumers", e);
            return List.of();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface EdcCreateAction {
        EdcIdResponseDto run();
    }
}
