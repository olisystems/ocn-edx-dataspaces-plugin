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

import edx.connector.enrichment.CdrCo2EnrichmentService;
import edx.connector.enrichment.EnrichedCdrDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${ocn.node.apiPrefix}/plugin/edx/cdrs")
@CrossOrigin(origins = "*")
public class EdxEnrichedCdrController {

    private final CdrCo2EnrichmentService enrichmentService;

    public EdxEnrichedCdrController(CdrCo2EnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    @GetMapping(value = "/enriched", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EnrichedCdrDto> getEnrichedCdr(
        @RequestParam String countryCode,
        @RequestParam String partyId,
        @RequestParam String cdrId,
        @RequestParam String zone
    ) {
        return enrichmentService.enrich(countryCode, partyId, cdrId, zone)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
