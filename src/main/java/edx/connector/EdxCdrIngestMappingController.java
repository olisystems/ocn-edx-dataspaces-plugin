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

import edx.connector.persistence.CdrIngestMappingStore;
import edx.connector.persistence.EdxCdrIngestMappingDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${ocn.node.apiPrefix}/plugin/edx/cdrs/mapping")
@CrossOrigin(origins = "*")
public class EdxCdrIngestMappingController {

    private final CdrIngestMappingStore mappingStore;

    public EdxCdrIngestMappingController(CdrIngestMappingStore mappingStore) {
        this.mappingStore = mappingStore;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EdxCdrIngestMappingDto> getMapping(
        @RequestParam String countryCode,
        @RequestParam String partyId,
        @RequestParam String cdrId
    ) {
        return mappingStore.find(countryCode, partyId, cdrId)
            .map(EdxCdrIngestMappingDto::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/by-service-id/{serviceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EdxCdrIngestMappingDto> getMappingByServiceId(@PathVariable String serviceId) {
        return mappingStore.findByServiceId(serviceId)
            .map(EdxCdrIngestMappingDto::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
