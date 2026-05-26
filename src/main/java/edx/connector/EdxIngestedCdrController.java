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

import edx.connector.cdrservice.CdrServiceClient;
import edx.connector.cdrservice.IngestedCdrLookup;
import edx.connector.cdrservice.RawCdrDto;
import edx.connector.cdrservice.ResolvedIngestedCdrDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${ocn.node.apiPrefix}/plugin/edx/cdrs")
@CrossOrigin(origins = "*")
public class EdxIngestedCdrController {

    private final CdrServiceClient cdrServiceClient;
    private final IngestedCdrLookup ingestedCdrLookup;

    public EdxIngestedCdrController(CdrServiceClient cdrServiceClient, IngestedCdrLookup ingestedCdrLookup) {
        this.cdrServiceClient = cdrServiceClient;
        this.ingestedCdrLookup = ingestedCdrLookup;
    }

    @GetMapping(value = "/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResolvedIngestedCdrDto> resolveIngestedCdr(
        @RequestParam(value = "countryCode", required = false) String countryCode,
        @RequestParam(value = "partyId", required = false) String partyId,
        @RequestParam(value = "cdrId", required = false) String cdrId,
        @RequestParam(value = "ocpiId", required = false) String ocpiId
    ) {
        String resolvedCdrId = hasText(cdrId) ? cdrId : ocpiId;
        return ingestedCdrLookup.resolve(countryCode, partyId, resolvedCdrId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/raw/{serviceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RawCdrDto> getRawCdrByServiceId(@PathVariable String serviceId) {
        RawCdrDto raw = cdrServiceClient.getRawCdrById(serviceId);
        return raw == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(raw);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
