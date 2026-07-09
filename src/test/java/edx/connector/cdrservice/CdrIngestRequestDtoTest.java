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

package edx.connector.cdrservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CdrIngestRequestDtoTest {

    @Test
    void formatsSourceAndTargetFromOcpiRoute() {
        CdrIngestRequestDto request = CdrIngestRequestDto.of("DE", "CPO", "FR", "EMS", java.util.Map.of("id", "cdr-1"));

        assertEquals("DE-CPO", request.source());
        assertEquals("FR-EMS", request.target());
        assertEquals("cdr-1", ((java.util.Map<?, ?>) request.cdr()).get("id"));
    }

    @Test
    void normalizesEndpointsToMatchAssetSourceKeys() {
        CdrIngestRequestDto request = CdrIngestRequestDto.of(" de ", "cpo", "fr", " ems", java.util.Map.of());

        assertEquals("DE-CPO", request.source());
        assertEquals("FR-EMS", request.target());
    }
}
