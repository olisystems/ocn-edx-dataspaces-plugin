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

import edx.connector.co2provider.Co2EmissionDataResponseDto;
import edx.connector.co2provider.Co2EmissionQuery;
import edx.connector.co2provider.Co2ProviderClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${ocn.node.apiPrefix}/plugin/edx")
@ConditionalOnExpression("!'${edx.co2.provider.publicApiUrl:}'.trim().isEmpty()")
@CrossOrigin(origins = "*")
public class EdxCo2Controller {

    private final Co2ProviderClient co2ProviderClient;

    public EdxCo2Controller(Co2ProviderClient co2ProviderClient) {
        this.co2ProviderClient = co2ProviderClient;
    }

    @GetMapping(value = "/co2", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Co2EmissionDataResponseDto> getCo2Data(
        @RequestParam("start") String start,
        @RequestParam("end") String end,
        @RequestParam("zone") String zone,
        @RequestParam(name = "time-resolution") String timeResolution,
        @RequestParam(name = "calculation-type") String calculationType,
        @RequestParam(name = "emission-type") String emissionType
    ) {
        Co2EmissionDataResponseDto body =
            co2ProviderClient.fetchEmissionData(
                new Co2EmissionQuery(start, end, zone, timeResolution, calculationType, emissionType)
            );
        return ResponseEntity.ok(body);
    }
}
