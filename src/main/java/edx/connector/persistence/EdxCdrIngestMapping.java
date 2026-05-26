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
    name = "edx_cdr_ingest_mapping",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_edx_cdr_ingest_mapping_ocpi_identity",
        columnNames = {"country_code", "party_id", "cdr_id"}
    )
)
public class EdxCdrIngestMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "party_id", nullable = false, length = 3)
    private String partyId;

    @Column(name = "cdr_id", nullable = false, length = 255)
    private String cdrId;

    @Column(name = "service_id", nullable = false, length = 255)
    private String serviceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EdxCdrIngestMapping() {
    }

    public EdxCdrIngestMapping(String countryCode, String partyId, String cdrId, String serviceId) {
        this.countryCode = countryCode;
        this.partyId = partyId;
        this.cdrId = cdrId;
        this.serviceId = serviceId;
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

    public String getCdrId() {
        return cdrId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
}
