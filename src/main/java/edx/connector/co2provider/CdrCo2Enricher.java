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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class CdrCo2Enricher {

    public Map<String, Object> enrich(Map<String, Object> sourceCdr, Co2EmissionDataResponseDto emissions, String zone) {
        Map<String, Object> cdr = deepCopyMap(sourceCdr);
        Map<String, Double> intensityByHour = indexHourlyIntensity(emissions);
        String unit = emissions.measurements().isEmpty() ? null : emissions.measurements().get(0).unit();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> periods = (List<Map<String, Object>>) cdr.get("charging_periods");
        if (periods == null || periods.isEmpty()) {
            return cdr;
        }

        List<Map<String, Object>> enrichedPeriods = new ArrayList<>(periods.size());
        double totalEmissionsG = 0.0;
        boolean hasTotal = false;

        for (Map<String, Object> period : periods) {
            Map<String, Object> enrichedPeriod = deepCopyMap(period);
            Double energyKwh = energyKwhFromPeriod(period);
            String periodStart = stringValue(period.get("start_date_time"));
            Double intensity = intensityForPeriodStart(periodStart, intensityByHour);

            if (energyKwh != null && intensity != null) {
                double emissionsG = energyKwh * intensity;
                enrichedPeriod.put(
                    "co2",
                    Map.of(
                        "grid_zone", zone,
                        "unit", unit == null ? "gCO2eqPerkWh" : unit,
                        "intensity_gco2eq_per_kwh", round(intensity, 4),
                        "emissions_gco2eq", round(emissionsG, 2),
                        "intensity_timestamp", hourKey(periodStart)
                    )
                );
                totalEmissionsG += emissionsG;
                hasTotal = true;
            }

            enrichedPeriods.add(enrichedPeriod);
        }

        cdr.put("charging_periods", enrichedPeriods);
        if (hasTotal) {
            cdr.put("co2_total_gco2eq", round(totalEmissionsG, 2));
        }
        return cdr;
    }

    static Map<String, Double> indexHourlyIntensity(Co2EmissionDataResponseDto emissions) {
        Map<String, Double> indexed = new LinkedHashMap<>();
        if (emissions == null || emissions.measurements() == null) {
            return indexed;
        }
        for (Co2MeasurementDto measurement : emissions.measurements()) {
            if (measurement.measurementValues() == null) {
                continue;
            }
            for (Co2MeasurementValueDto value : measurement.measurementValues()) {
                if (value.timestamp() != null) {
                    indexed.put(hourKey(value.timestamp()), value.value());
                }
            }
        }
        return indexed;
    }

    static Double intensityForPeriodStart(String periodStart, Map<String, Double> intensityByHour) {
        if (periodStart == null || periodStart.isBlank()) {
            return null;
        }
        return intensityByHour.get(hourKey(periodStart));
    }

    static String hourKey(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.length() < 13) {
            return normalizeTimestamp(isoTimestamp);
        }
        return isoTimestamp.substring(0, 13) + ":00:00Z";
    }

    /** Hourly emissions API expects whole-hour bounds; sub-hour windows return no data. */
    public static String co2QueryWindowStart(String isoTimestamp) {
        return hourKey(isoTimestamp);
    }

    public static String co2QueryWindowEnd(String isoTimestamp) {
        String hour = hourKey(isoTimestamp);
        if (hour == null) {
            return null;
        }
        return Instant.parse(hour).plus(1, ChronoUnit.HOURS).toString();
    }

    static Double energyKwhFromPeriod(Map<String, Object> period) {
        Object dimensionsObj = period.get("dimensions");
        if (!(dimensionsObj instanceof List<?> dimensions)) {
            return null;
        }
        for (Object dimensionObj : dimensions) {
            if (!(dimensionObj instanceof Map<?, ?> dimension)) {
                continue;
            }
            if ("ENERGY".equalsIgnoreCase(stringValue(dimension.get("type")))) {
                return doubleValue(dimension.get("volume"));
            }
        }
        return null;
    }

    public static String chargingWindowStart(Map<String, Object> cdr) {
        String cdrStart = stringValue(cdr.get("start_date_time"));
        String earliestPeriod = earliestPeriodStart(cdr);
        return earliest(cdrStart, earliestPeriod);
    }

    public static String chargingWindowEnd(Map<String, Object> cdr) {
        String cdrEnd = stringValue(cdr.get("end_date_time"));
        String latestPeriod = latestPeriodStart(cdr);
        return latest(cdrEnd, latestPeriod);
    }

    private static String earliestPeriodStart(Map<String, Object> cdr) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> periods = (List<Map<String, Object>>) cdr.get("charging_periods");
        if (periods == null || periods.isEmpty()) {
            return null;
        }
        String earliest = null;
        for (Map<String, Object> period : periods) {
            String start = stringValue(period.get("start_date_time"));
            if (start != null && (earliest == null || start.compareTo(earliest) < 0)) {
                earliest = start;
            }
        }
        return earliest;
    }

    private static String latestPeriodStart(Map<String, Object> cdr) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> periods = (List<Map<String, Object>>) cdr.get("charging_periods");
        if (periods == null || periods.isEmpty()) {
            return null;
        }
        String latest = null;
        for (Map<String, Object> period : periods) {
            String start = stringValue(period.get("start_date_time"));
            if (start != null && (latest == null || start.compareTo(latest) > 0)) {
                latest = start;
            }
        }
        return latest;
    }

    private static String earliest(String left, String right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static String latest(String left, String right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.compareTo(right) >= 0 ? left : right;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) mapValue));
            } else if (value instanceof List<?> listValue) {
                copy.put(entry.getKey(), deepCopyList(listValue));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> deepCopyList(List<?> source) {
        List<Object> copy = new ArrayList<>(source.size());
        for (Object item : source) {
            if (item instanceof Map<?, ?> mapItem) {
                copy.add(deepCopyMap((Map<String, Object>) mapItem));
            } else if (item instanceof List<?> listItem) {
                copy.add(deepCopyList(listItem));
            } else {
                copy.add(item);
            }
        }
        return copy;
    }

    private static String normalizeTimestamp(String timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.endsWith("Z") ? timestamp : timestamp;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, null);
        return text == null || text.isBlank() ? null : text;
    }

    private static Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }
}
