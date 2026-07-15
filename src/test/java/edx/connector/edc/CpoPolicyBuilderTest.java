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

class CpoPolicyBuilderTest {

    @Test
    void buildsOpenPolicyWhenNoConsumers() {
        Map<String, Object> definition = CpoPolicyBuilder.buildPolicyDefinition("policy-access-DE-CPO", List.of());
        assertEquals("PolicyDefinition", definition.get("@type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) definition.get("policy");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) policy.get("permission");
        assertEquals(1, permissions.size());
        assertEquals("use", permissions.getFirst().get("action"));
        assertTrue(((List<?>) permissions.getFirst().get("constraint")).isEmpty());
    }

    @Test
    void buildsIdentityConstraintForDidConsumer() {
        String did = "did:web:wallet-api.oid.spherity.dev:did:da0a075b-1cbf-47a8-91f9-51a64fee0116";
        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) CpoPolicyBuilder.buildPolicyDefinition(
            "policy-access-DE-CPO",
            List.of(new PolicyConsumerSubject(PolicyConsumerSubjectType.DID, did))
        ).get("policy");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) policy.get("permission");
        @SuppressWarnings("unchecked")
        Map<String, Object> constraint = (Map<String, Object>) permissions.getFirst().get("constraint");
        assertEquals("identity", constraint.get("leftOperand"));
        assertEquals("eq", constraint.get("operator"));
        assertEquals(did, constraint.get("rightOperand"));
    }

    @Test
    void buildsMarketPartnerIdConstraint() {
        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) CpoPolicyBuilder.buildPolicyDefinition(
            "policy-access-DE-CPO",
            List.of(new PolicyConsumerSubject(PolicyConsumerSubjectType.MARKET_PARTNER, "4045399000008"))
        ).get("policy");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) policy.get("permission");
        @SuppressWarnings("unchecked")
        Map<String, Object> constraint = (Map<String, Object>) permissions.getFirst().get("constraint");
        assertEquals("MarketPartner.mpId", constraint.get("leftOperand"));
        assertEquals("4045399000008", constraint.get("rightOperand"));
    }

    @Test
    void extractConsumersRoundTripsBuiltPolicy() {
        List<PolicyConsumerSubject> consumers = List.of(
            new PolicyConsumerSubject(PolicyConsumerSubjectType.DID, "did:web:example:alice"),
            new PolicyConsumerSubject(PolicyConsumerSubjectType.MARKET_PARTNER, "4045399000008")
        );

        List<PolicyConsumerSubject> extracted = CpoPolicyBuilder.extractConsumers(
            CpoPolicyBuilder.buildPolicyDefinition("policy-access-DE-CPO", consumers)
        );

        assertEquals(consumers, extracted);
        assertEquals(
            List.of(new PolicyConsumerSubject(PolicyConsumerSubjectType.DID, "did:web:example:alice")),
            CpoPolicyBuilder.extractConsumers(
                CpoPolicyBuilder.buildPolicyDefinition(
                    "policy-access-DE-CPO",
                    List.of(new PolicyConsumerSubject(PolicyConsumerSubjectType.DID, "did:web:example:alice"))
                )
            )
        );
        assertTrue(
            CpoPolicyBuilder.extractConsumers(
                CpoPolicyBuilder.buildPolicyDefinition("policy-access-DE-CPO", List.of())
            ).isEmpty()
        );
    }

    @Test
    void extractConsumersReadsOdrlPrefixedJsonLdForm() {
        Map<String, Object> definition = Map.of(
            "@id", "policy-access-DE-CPO",
            "policy", Map.of(
                "odrl:permission", Map.of(
                    "odrl:action", Map.of("@id", "odrl:use"),
                    "odrl:constraint", Map.of(
                        "odrl:or", List.of(
                            Map.of(
                                "odrl:leftOperand", Map.of("@id", "edc:identity"),
                                "odrl:operator", Map.of("@id", "odrl:eq"),
                                "odrl:rightOperand", "did:web:example:alice"
                            ),
                            Map.of(
                                "odrl:leftOperand", Map.of("@id", "edc:MarketPartner.mpId"),
                                "odrl:operator", Map.of("@id", "odrl:eq"),
                                "odrl:rightOperand", "4045399000008"
                            )
                        )
                    )
                )
            )
        );

        assertEquals(
            List.of(
                new PolicyConsumerSubject(PolicyConsumerSubjectType.DID, "did:web:example:alice"),
                new PolicyConsumerSubject(PolicyConsumerSubjectType.MARKET_PARTNER, "4045399000008")
            ),
            CpoPolicyBuilder.extractConsumers(definition)
        );
    }

    @Test
    void buildsOrConstraintForMixedConsumerTypes() {
        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) CpoPolicyBuilder.buildPolicyDefinition(
            "policy-access-DE-CPO",
            List.of(
                new PolicyConsumerSubject(PolicyConsumerSubjectType.DID, "did:web:example:alice"),
                new PolicyConsumerSubject(PolicyConsumerSubjectType.MARKET_PARTNER, "4045399000008")
            )
        ).get("policy");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) policy.get("permission");
        @SuppressWarnings("unchecked")
        Map<String, Object> constraint = (Map<String, Object>) permissions.getFirst().get("constraint");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orConstraints = (List<Map<String, Object>>) constraint.get("or");
        assertEquals(2, orConstraints.size());
        assertEquals("identity", orConstraints.get(0).get("leftOperand"));
        assertEquals("MarketPartner.mpId", orConstraints.get(1).get("leftOperand"));
        assertEquals("4045399000008", orConstraints.get(1).get("rightOperand"));
    }
}
