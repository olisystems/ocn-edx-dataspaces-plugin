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

import com.fasterxml.jackson.databind.ObjectMapper;
import edx.connector.cdrservice.CdrServiceClient;
import edx.connector.cdrservice.IngestedCdrLookup;
import edx.connector.enrichment.CdrCo2EnrichmentService;
import edx.connector.co2provider.Co2EnrichmentDefaults;
import edx.connector.co2provider.Co2ProviderClient;
import java.net.URI;
import java.time.Duration;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import snc.openchargingnetwork.node.components.HttpClientComponent;
import edx.connector.persistence.EdxCdrIngestMapping;
import edx.connector.persistence.EdxCdrIngestMappingRepository;

@Configuration
@ComponentScan(
    basePackageClasses = EdxConnectorAutoConfiguration.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = EdxEnrichedCdrController.class
    )
)
@EntityScan(basePackageClasses = EdxCdrIngestMapping.class)
@EnableJpaRepositories(basePackageClasses = EdxCdrIngestMappingRepository.class)
@ConditionalOnProperty(prefix = "edx.cdr.service", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EdxConnectorAutoConfiguration {

    private static final Logger LOGGER = Logger.getLogger(EdxConnectorAutoConfiguration.class.getName());
    static final int DEFAULT_TIMEOUT_MS = 5_000;

    static {
        String version = EdxConnectorAutoConfiguration.class.getPackage().getImplementationVersion();
        LOGGER.info(
            "EDX plugin loaded"
                + (version != null && !version.isBlank() ? " (version " + version + ")" : "")
                + "; CDR mapping saved when ingest returns rawRecordId (success=false is ok)"
        );
    }

    @Bean
    public CdrServiceClient cdrServiceClient(Environment environment, HttpClientComponent httpClientComponent) {
        URI baseUri = URI.create(readRequiredString(
            environment,
            "edx.cdr.service.baseUrl",
            "EDX_CDR_SERVICE_BASE_URL"
        ));
        String apiKey = readString(environment, "edx.cdr.service.apiKey", "EDX_CDR_SERVICE_API_KEY", "");
        int timeoutMs = readInt(environment, "edx.cdr.service.timeoutMs", "EDX_CDR_SERVICE_TIMEOUT_MS", DEFAULT_TIMEOUT_MS);

        if (apiKey.isBlank()) {
            LOGGER.warning("EDX CDR service API key is not configured; requests may be rejected");
        }

        LOGGER.info(
            "EDX CDR service connector enabled; baseUrl=" + baseUri +
                ", apiKeyConfigured=" + !apiKey.isBlank()
        );
        return new CdrServiceClient(
            baseUri,
            apiKey,
            Duration.ofMillis(timeoutMs),
            cdrObjectMapper(httpClientComponent)
        );
    }

    @Bean(destroyMethod = "shutdown")
    public CdrForwarder cdrForwarder(
        CdrServiceClient cdrServiceClient,
        edx.connector.persistence.CdrIngestMappingStore mappingStore
    ) {
        return new CdrForwarder(cdrServiceClient, mappingStore);
    }

    @Bean
    public IngestedCdrLookup ingestedCdrLookup(
        CdrServiceClient cdrServiceClient,
        edx.connector.persistence.CdrIngestMappingStore mappingStore
    ) {
        return new IngestedCdrLookup(cdrServiceClient, mappingStore);
    }

    @Bean
    @ConditionalOnExpression("!'${edx.co2.provider.publicApiUrl:}'.trim().isEmpty()")
    public Co2ProviderClient co2ProviderClient(Environment environment, HttpClientComponent httpClientComponent) {
        URI publicApiUri = URI.create(
            readRequiredString(
                environment,
                "edx.co2.provider.publicApiUrl",
                "EDX_CO2_PROVIDER_PUBLIC_API_URL"
            )
        );
        String token = readRequiredString(
            environment,
            "edx.co2.provider.token",
            "EDX_CO2_PROVIDER_TOKEN"
        );
        int timeoutMs = readInt(
            environment,
            "edx.co2.provider.timeoutMs",
            "EDX_CO2_PROVIDER_TIMEOUT_MS",
            DEFAULT_TIMEOUT_MS
        );
        LOGGER.info("EDX CO2 provider client enabled; publicApiUrl=" + publicApiUri);
        return new Co2ProviderClient(
            publicApiUri,
            token,
            Duration.ofMillis(timeoutMs),
            cdrObjectMapper(httpClientComponent)
        );
    }

    @Bean
    @ConditionalOnExpression("!'${edx.co2.provider.publicApiUrl:}'.trim().isEmpty()")
    public CdrCo2EnrichmentService cdrCo2EnrichmentService(
        edx.connector.persistence.CdrIngestMappingStore mappingStore,
        CdrServiceClient cdrServiceClient,
        Co2ProviderClient co2ProviderClient,
        Environment environment
    ) {
        return new CdrCo2EnrichmentService(
            mappingStore,
            cdrServiceClient,
            co2ProviderClient,
            new Co2EnrichmentDefaults(
                readString(environment, "edx.co2.enrichment.timeResolution", "EDX_CO2_ENRICHMENT_TIME_RESOLUTION", "Hourly"),
                readString(environment, "edx.co2.enrichment.calculationType", "EDX_CO2_ENRICHMENT_CALCULATION_TYPE", "Consumption"),
                readString(environment, "edx.co2.enrichment.emissionType", "EDX_CO2_ENRICHMENT_EMISSION_TYPE", "Lifecycle")
            )
        );
    }

    @Bean
    @ConditionalOnExpression("!'${edx.co2.provider.publicApiUrl:}'.trim().isEmpty()")
    public EdxEnrichedCdrController edxEnrichedCdrController(CdrCo2EnrichmentService cdrCo2EnrichmentService) {
        LOGGER.info("EDX enriched CDR endpoint enabled at {apiPrefix}/plugin/edx/cdrs/enriched");
        return new EdxEnrichedCdrController(cdrCo2EnrichmentService);
    }

    static String readRequiredString(Environment environment, String property, String envName) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration: set " + property + " or " + envName);
        }
        return value.trim();
    }

    private static String readString(Environment environment, String property, String envName, String defaultValue) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int readInt(Environment environment, String property, String envName, int defaultValue) {
        String value = readString(environment, property, envName, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid " + property + " value '" + value + "', using " + defaultValue);
            return defaultValue;
        }
    }

    private static ObjectMapper cdrObjectMapper(HttpClientComponent httpClientComponent) {
        try {
            return httpClientComponent.getMapper();
        } catch (Exception e) {
            LOGGER.warning("Unable to use node ObjectMapper; falling back to default mapper: " + e.getMessage());
            return new ObjectMapper();
        }
    }
}
