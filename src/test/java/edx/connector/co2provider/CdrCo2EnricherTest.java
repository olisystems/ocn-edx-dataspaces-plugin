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

package edx.connector.co2provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CdrCo2EnricherTest {

    @Test
    void enrichAddsCo2PropertyToChargingPeriods() {
        Map<String, Object> cdr = new LinkedHashMap<>();
        cdr.put("start_date_time", "2026-02-18T09:15:00Z");
        cdr.put("end_date_time", "2026-02-18T10:45:00Z");
        cdr.put(
            "charging_periods",
            List.of(
                Map.of(
                    "start_date_time", "2026-02-18T09:15:00Z",
                    "dimensions", List.of(Map.of("type", "ENERGY", "volume", 8.5))
                )
            )
        );

        Co2EmissionDataResponseDto emissions = new Co2EmissionDataResponseDto(
            "2026-02-18T09:00:00Z",
            "2026-02-18T11:00:00Z",
            List.of(
                new Co2MeasurementDto(
                    "gCO2eqPerkWh",
                    "DE_LU",
                    "Hourly",
                    "Consumption",
                    "Lifecycle",
                    List.of(
                        new Co2MeasurementValueDto("2026-02-18T09:00:00Z", "2026-01-01T00:00:00Z", 487.0, "Actual"),
                        new Co2MeasurementValueDto("2026-02-18T10:00:00Z", "2026-01-01T00:00:00Z", 420.0, "Actual")
                    )
                )
            )
        );

        Map<String, Object> enriched = new CdrCo2Enricher().enrich(cdr, emissions, "DE_LU");

        @SuppressWarnings("unchecked")
        Map<String, Object> co2 = (Map<String, Object>) ((List<Map<String, Object>>) enriched.get("charging_periods")).get(0).get("co2");
        assertNotNull(co2);
        assertEquals(487.0, co2.get("intensity_gco2eq_per_kwh"));
        assertEquals(4139.5, co2.get("emissions_gco2eq"));
        assertEquals(4139.5, enriched.get("co2_total_gco2eq"));
    }

    @Test
    void chargingWindowUsesSessionStartWhenPeriodStartsLater() {
        Map<String, Object> cdr = Map.of(
            "start_date_time", "2025-05-21T11:33:27.538Z",
            "end_date_time", "2025-05-21T11:37:59.114Z",
            "charging_periods", List.of(
                Map.of(
                    "start_date_time", "2025-05-21T11:37:59.114Z",
                    "dimensions", List.of(Map.of("type", "ENERGY", "volume", 3.035))
                )
            )
        );

        assertEquals("2025-05-21T11:33:27.538Z", CdrCo2Enricher.chargingWindowStart(cdr));
        assertEquals("2025-05-21T11:37:59.114Z", CdrCo2Enricher.chargingWindowEnd(cdr));
        assertEquals("2025-05-21T11:00:00Z", CdrCo2Enricher.co2QueryWindowStart(cdr.get("start_date_time").toString()));
        assertEquals("2025-05-21T12:00:00Z", CdrCo2Enricher.co2QueryWindowEnd(cdr.get("end_date_time").toString()));
    }

    @Test
    void enrichAddsCo2ForBanulaStylePeriodWhenHourlyIntensityAvailable() {
        Map<String, Object> cdr = Map.of(
            "start_date_time", "2025-05-21T11:33:27.538Z",
            "end_date_time", "2025-05-21T11:37:59.114Z",
            "charging_periods", List.of(
                Map.of(
                    "start_date_time", "2025-05-21T11:37:59.114Z",
                    "dimensions", List.of(Map.of("type", "ENERGY", "volume", 3.035))
                )
            )
        );

        Co2EmissionDataResponseDto emissions = new Co2EmissionDataResponseDto(
            "2025-05-21T11:00:00Z",
            "2025-05-21T12:00:00Z",
            List.of(
                new Co2MeasurementDto(
                    "gCO2eqPerkWh",
                    "DE_LU",
                    "Hourly",
                    "Consumption",
                    "Lifecycle",
                    List.of(
                        new Co2MeasurementValueDto("2025-05-21T11:00:00Z", "2026-01-31T03:45:40Z", 185.9731, "Actual")
                    )
                )
            )
        );

        Map<String, Object> enriched = new CdrCo2Enricher().enrich(cdr, emissions, "DE_LU");

        @SuppressWarnings("unchecked")
        Map<String, Object> co2 = (Map<String, Object>) ((List<Map<String, Object>>) enriched.get("charging_periods")).get(0).get("co2");
        assertNotNull(co2);
        assertEquals(185.9731, co2.get("intensity_gco2eq_per_kwh"));
        assertEquals(564.43, co2.get("emissions_gco2eq"));
    }
}
