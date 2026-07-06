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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CpoAssetFactory {

    private CpoAssetFactory() {
    }

    public static Map<String, Object> buildAsset(
        String assetId,
        String sourceKey,
        String co2RelevantCdrUrl,
        String cdrServiceApiKey
    ) {
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("@context", edcContext());
        asset.put("@id", assetId);
        asset.put("properties", Map.of(
            "name",
            "CO2-relevant CDR data from " + sourceKey,
            "contenttype",
            "application/json"
        ));
        asset.put("dataAddress", buildDataAddress(sourceKey, co2RelevantCdrUrl, cdrServiceApiKey));
        return asset;
    }

    public static Map<String, Object> buildContractDefinition(
        String contractDefinitionId,
        String assetId,
        String accessPolicyId,
        String contractPolicyId
    ) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("@context", edcContext());
        definition.put("@id", contractDefinitionId);
        definition.put("accessPolicyId", accessPolicyId);
        definition.put("contractPolicyId", contractPolicyId);
        definition.put("assetsSelector", List.of(buildAssetIdCriterion(assetId)));
        return definition;
    }

    private static Map<String, Object> buildDataAddress(String sourceKey, String co2RelevantCdrUrl, String cdrServiceApiKey) {
        Map<String, Object> dataAddress = new LinkedHashMap<>();
        dataAddress.put("type", "HttpData");
        dataAddress.put("baseUrl", co2RelevantCdrUrl);
        dataAddress.put("authKey", "x-api-key");
        dataAddress.put("authCode", cdrServiceApiKey);
        dataAddress.put("proxyBody", "true");
        dataAddress.put("proxyPath", "true");
        dataAddress.put("proxyMethod", "true");
        dataAddress.put("proxyQueryParams", "true");
        dataAddress.put("header:x-source", sourceKey);
        dataAddress.put("header:x-target", "");
        return dataAddress;
    }

    private static Map<String, Object> buildAssetIdCriterion(String assetId) {
        Map<String, Object> criterion = new LinkedHashMap<>();
        criterion.put("@type", "Criterion");
        criterion.put("operandLeft", "id");
        criterion.put("operator", "=");
        criterion.put("operandRight", assetId);
        return criterion;
    }

    private static Map<String, String> edcContext() {
        return Map.of("@vocab", "https://w3id.org/edc/v0.0.1/ns/");
    }
}
