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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CpoAssetFactoryTest {

    @Test
    void buildsAssetWithSourceHeaderAndOmitsEmptyTarget() {
        Map<String, Object> asset = CpoAssetFactory.buildAsset(
            "cdr-data:src:DE-CPO:tgt:",
            "DE-CPO",
            "https://cdr.example.com/api/v1/co2-relevant-cdr",
            "secret"
        );

        assertEquals("cdr-data:src:DE-CPO:tgt:", asset.get("@id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dataAddress = (Map<String, Object>) asset.get("dataAddress");
        assertEquals("https://cdr.example.com/api/v1/co2-relevant-cdr", dataAddress.get("baseUrl"));
        assertEquals("DE-CPO", dataAddress.get("header:x-source"));
        assertTrue(!dataAddress.containsKey("header:x-target"));
        assertEquals("secret", dataAddress.get("authCode"));
    }

    @Test
    void buildsContractDefinitionForAsset() {
        Map<String, Object> definition = CpoAssetFactory.buildContractDefinition(
            "contract-def-DE-CPO",
            "cdr-data:src:DE-CPO:tgt:",
            "policy-access-DE-CPO",
            "policy-contract-DE-CPO"
        );

        assertEquals("contract-def-DE-CPO", definition.get("@id"));
        assertEquals("policy-access-DE-CPO", definition.get("accessPolicyId"));
        assertEquals("policy-contract-DE-CPO", definition.get("contractPolicyId"));
        assertTrue(((List<?>) definition.get("assetsSelector")).size() == 1);
    }
}
