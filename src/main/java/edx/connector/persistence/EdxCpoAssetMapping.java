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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "edx_cpo_asset_mapping",
    uniqueConstraints = @UniqueConstraint(columnNames = { "country_code", "party_id" })
)
public class EdxCpoAssetMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "party_id", nullable = false, length = 64)
    private String partyId;

    @Column(name = "source_key", nullable = false, length = 128)
    private String sourceKey;

    @Column(name = "asset_id", nullable = false, length = 256)
    private String assetId;

    @Column(name = "access_policy_id", nullable = false, length = 256)
    private String accessPolicyId;

    @Column(name = "contract_policy_id", nullable = false, length = 256)
    private String contractPolicyId;

    @Column(name = "contract_definition_id", nullable = false, length = 256)
    private String contractDefinitionId;

    @Column(name = "allowed_consumers_json", columnDefinition = "text")
    private String allowedConsumersJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EdxCpoAssetMapping() {
    }

    public EdxCpoAssetMapping(
        String countryCode,
        String partyId,
        String sourceKey,
        String assetId,
        String accessPolicyId,
        String contractPolicyId,
        String contractDefinitionId,
        String allowedConsumersJson
    ) {
        this.countryCode = countryCode;
        this.partyId = partyId;
        this.sourceKey = sourceKey;
        this.assetId = assetId;
        this.accessPolicyId = accessPolicyId;
        this.contractPolicyId = contractPolicyId;
        this.contractDefinitionId = contractDefinitionId;
        this.allowedConsumersJson = allowedConsumersJson;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPartyId() {
        return partyId;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getAccessPolicyId() {
        return accessPolicyId;
    }

    public String getContractPolicyId() {
        return contractPolicyId;
    }

    public String getContractDefinitionId() {
        return contractDefinitionId;
    }

    public String getAllowedConsumersJson() {
        return allowedConsumersJson;
    }

    public void setAllowedConsumersJson(String allowedConsumersJson) {
        this.allowedConsumersJson = allowedConsumersJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
