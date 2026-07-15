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

public record Co2EnrichmentDefaults(
    String timeResolution,
    String calculationType,
    String emissionType
) {

    public static Co2EnrichmentDefaults standard() {
        return new Co2EnrichmentDefaults("Hourly", "Consumption", "Lifecycle");
    }
}
