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

import edx.connector.persistence.JpaCpoAssetMappingStore;
import java.util.Objects;
import org.springframework.stereotype.Service;
import snc.openchargingnetwork.node.models.entities.PlatformEntity;
import snc.openchargingnetwork.node.models.entities.RoleEntity;
import snc.openchargingnetwork.node.models.ocpi.Role;
import snc.openchargingnetwork.node.repositories.PlatformRepository;
import snc.openchargingnetwork.node.repositories.RoleRepository;

@Service
public final class CpoPolicyAuthService {

    private final PlatformRepository platformRepository;
    private final RoleRepository roleRepository;

    public CpoPolicyAuthService(PlatformRepository platformRepository, RoleRepository roleRepository) {
        this.platformRepository = platformRepository;
        this.roleRepository = roleRepository;
    }

    public void assertCpoOwner(String authorizationHeader, String countryCode, String partyId) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new CpoPolicyAuthorizationException("Missing Authorization header");
        }
        String token = extractToken(authorizationHeader);
        PlatformEntity platform = platformRepository.findByAuth_TokenC(token);
        if (platform == null) {
            platform = platformRepository.findByAuth_TokenA(token);
        }
        if (platform == null) {
            throw new CpoPolicyAuthorizationException("Invalid OCPI token");
        }

        String normalizedCountry = JpaCpoAssetMappingStore.normalizeCountryCode(countryCode);
        String normalizedParty = JpaCpoAssetMappingStore.normalizePartyId(partyId);
        Long platformId = platform.getId();
        boolean ownsCpoRole = false;
        for (RoleEntity role : roleRepository.findAllByCountryCodeAndPartyIDAllIgnoreCase(normalizedCountry, normalizedParty)) {
            if (role.getRole() == Role.CPO && Objects.equals(role.getPlatformID(), platformId)) {
                ownsCpoRole = true;
                break;
            }
        }
        if (!ownsCpoRole) {
            throw new CpoPolicyAuthorizationException(
                "Token is not authorized to manage policies for CPO " + normalizedCountry + "/" + normalizedParty
            );
        }
    }

    static String extractToken(String authorizationHeader) {
        String trimmed = authorizationHeader.trim();
        int space = trimmed.lastIndexOf(' ');
        if (space >= 0 && space + 1 < trimmed.length()) {
            return trimmed.substring(space + 1).trim();
        }
        return trimmed;
    }
}
