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
import edx.connector.persistence.CpoAssetMappingStore;
import edx.connector.persistence.EdxCpoAssetMapping;
import edx.connector.persistence.JpaCpoAssetMappingStore;
import java.util.List;
import java.util.Map;

public final class CpoPolicyUpdateService {

    private final EdcManagementClient edcManagementClient;
    private final CpoAssetMappingStore mappingStore;
    private final CpoAssetProvisioningService provisioningService;
    private final ObjectMapper mapper;

    public CpoPolicyUpdateService(
        EdcManagementClient edcManagementClient,
        CpoAssetMappingStore mappingStore,
        CpoAssetProvisioningService provisioningService,
        ObjectMapper mapper
    ) {
        this.edcManagementClient = edcManagementClient;
        this.mappingStore = mappingStore;
        this.provisioningService = provisioningService;
        this.mapper = mapper;
    }

    public EdxCpoAssetMapping replaceConsumers(String countryCode, String partyId, List<PolicyConsumerSubject> consumers) {
        EdxCpoAssetMapping mapping = requireMapping(countryCode, partyId);
        return applyConsumers(mapping, CpoPolicyBuilder.mergeConsumers(List.of(), consumers));
    }

    public EdxCpoAssetMapping addConsumers(String countryCode, String partyId, List<PolicyConsumerSubject> additions) {
        EdxCpoAssetMapping mapping = requireMapping(countryCode, partyId);
        List<PolicyConsumerSubject> merged = CpoPolicyBuilder.mergeConsumers(
            provisioningService.deserializeConsumers(mapping.getAllowedConsumersJson()),
            additions
        );
        return applyConsumers(mapping, merged);
    }

    private EdxCpoAssetMapping applyConsumers(EdxCpoAssetMapping mapping, List<PolicyConsumerSubject> consumers) {
        Map<String, Object> accessPolicy = CpoPolicyBuilder.buildPolicyDefinition(mapping.getAccessPolicyId(), consumers);
        Map<String, Object> contractPolicy = CpoPolicyBuilder.buildPolicyDefinition(mapping.getContractPolicyId(), consumers);
        // Persist local mapping only after both EDC updates succeed so DB does not claim a
        // consumer set that EDC never fully accepted. Partial EDC divergence (access updated,
        // contract failed) still requires an operator retry of the same request.
        edcManagementClient.updatePolicyDefinition(accessPolicy);
        edcManagementClient.updatePolicyDefinition(contractPolicy);
        String json = serializeConsumers(consumers);
        return mappingStore.updateAllowedConsumers(mapping, json);
    }

    private EdxCpoAssetMapping requireMapping(String countryCode, String partyId) {
        return mappingStore.find(
            JpaCpoAssetMappingStore.normalizeCountryCode(countryCode),
            JpaCpoAssetMappingStore.normalizePartyId(partyId)
        ).orElseThrow(() -> new CpoAssetNotFoundException(countryCode, partyId));
    }

    private String serializeConsumers(List<PolicyConsumerSubject> consumers) {
        try {
            return mapper.writeValueAsString(consumers);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize policy consumers", e);
        }
    }
}
