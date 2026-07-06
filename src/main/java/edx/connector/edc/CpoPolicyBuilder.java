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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CpoPolicyBuilder {

    private static final String IDENTITY_LEFT_OPERAND = "identity";
    private static final String MARKET_PARTNER_ID_LEFT_OPERAND = "MarketPartner.mpId";
    private static final List<String> POLICY_DEFINITION_CONTEXT = List.of(
        "https://w3id.org/edc/connector/management/v0.0.1"
    );

    private CpoPolicyBuilder() {
    }

    public static Map<String, Object> buildPolicyDefinition(String policyId, List<PolicyConsumerSubject> consumers) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("@context", POLICY_DEFINITION_CONTEXT);
        definition.put("@type", "PolicyDefinition");
        definition.put("@id", policyId);
        definition.put("policy", buildPolicy(consumers));
        return definition;
    }

    private static Map<String, Object> buildPolicy(List<PolicyConsumerSubject> consumers) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@type", "Set");
        policy.put("permission", List.of(buildPermission(consumers)));
        policy.put("prohibition", List.of());
        policy.put("obligation", List.of());
        return policy;
    }

    private static Map<String, Object> buildPermission(List<PolicyConsumerSubject> consumers) {
        Map<String, Object> permission = new LinkedHashMap<>();
        permission.put("action", "use");
        if (consumers == null || consumers.isEmpty()) {
            permission.put("constraint", List.of());
            return permission;
        }
        if (consumers.size() == 1) {
            permission.put("constraint", buildConsumerConstraint(consumers.getFirst()));
            return permission;
        }
        Map<String, Object> orConstraint = new LinkedHashMap<>();
        orConstraint.put("or", consumers.stream().map(CpoPolicyBuilder::buildConsumerConstraint).toList());
        permission.put("constraint", orConstraint);
        return permission;
    }

    private static Map<String, Object> buildConsumerConstraint(PolicyConsumerSubject consumer) {
        Map<String, Object> constraint = new LinkedHashMap<>();
        constraint.put("leftOperand", leftOperand(consumer.type()));
        constraint.put("operator", "eq");
        constraint.put("rightOperand", consumer.id());
        return constraint;
    }

    private static String leftOperand(PolicyConsumerSubjectType type) {
        if (type == PolicyConsumerSubjectType.DID) {
            return IDENTITY_LEFT_OPERAND;
        }
        return MARKET_PARTNER_ID_LEFT_OPERAND;
    }

    public static List<PolicyConsumerSubject> mergeConsumers(
        List<PolicyConsumerSubject> existing,
        List<PolicyConsumerSubject> additions
    ) {
        List<PolicyConsumerSubject> merged = new ArrayList<>(existing == null ? List.of() : existing);
        if (additions == null) {
            return merged;
        }
        for (PolicyConsumerSubject addition : additions) {
            if (addition == null || addition.id() == null || addition.id().isBlank()) {
                continue;
            }
            PolicyConsumerSubject normalized = new PolicyConsumerSubject(
                addition.type() == null ? PolicyConsumerSubjectType.MARKET_PARTNER : addition.type(),
                addition.id().trim()
            );
            boolean duplicate = merged.stream().anyMatch(
                subject -> subject.type() == normalized.type() && subject.id().equalsIgnoreCase(normalized.id())
            );
            if (!duplicate) {
                merged.add(normalized);
            }
        }
        return merged;
    }
}
