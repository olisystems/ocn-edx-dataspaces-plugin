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

package edx.connector;

import edx.connector.edc.CpoPolicyAuthService;
import edx.connector.edc.CpoPolicyUpdateService;
import edx.connector.edc.UpdateCpoPolicyRequestDto;
import edx.connector.persistence.EdxCpoAssetMappingDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${ocn.node.apiPrefix}/plugin/edx/dataspace/cpos/{countryCode}/{partyId}/policy")
@CrossOrigin(origins = "*")
@ConditionalOnProperty(prefix = "edx.edc.management", name = "enabled", havingValue = "true")
public class EdxCpoPolicyController {

    private final CpoPolicyAuthService authService;
    private final CpoPolicyUpdateService policyUpdateService;

    public EdxCpoPolicyController(CpoPolicyAuthService authService, CpoPolicyUpdateService policyUpdateService) {
        this.authService = authService;
        this.policyUpdateService = policyUpdateService;
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EdxCpoAssetMappingDto> replacePolicyConsumers(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String countryCode,
        @PathVariable String partyId,
        @RequestBody UpdateCpoPolicyRequestDto request
    ) {
        authService.assertCpoOwner(authorization, countryCode, partyId);
        return ResponseEntity.ok(
            EdxCpoAssetMappingDto.from(
                policyUpdateService.replaceConsumers(countryCode, partyId, request.consumers())
            )
        );
    }

    @PostMapping(
        path = "/consumers",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<EdxCpoAssetMappingDto> addPolicyConsumers(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String countryCode,
        @PathVariable String partyId,
        @RequestBody UpdateCpoPolicyRequestDto request
    ) {
        authService.assertCpoOwner(authorization, countryCode, partyId);
        return ResponseEntity.ok(
            EdxCpoAssetMappingDto.from(
                policyUpdateService.addConsumers(countryCode, partyId, request.consumers())
            )
        );
    }
}
