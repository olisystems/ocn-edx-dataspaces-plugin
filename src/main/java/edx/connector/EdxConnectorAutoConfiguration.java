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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edx.connector.cdrservice.CdrServiceClient;
import edx.connector.cdrservice.CdrServicePaths;
import edx.connector.cdrservice.IngestedCdrLookup;
import edx.connector.edc.CpoAssetProvisioningService;
import edx.connector.edc.CpoPolicyUpdateService;
import edx.connector.edc.EdcAssetSettings;
import edx.connector.edc.EdcManagementClient;
import edx.connector.edc.PolicyConsumerSubject;
import edx.connector.enrichment.CdrCo2EnrichmentService;
import edx.connector.co2provider.Co2EnrichmentDefaults;
import edx.connector.co2provider.Co2ProviderClient;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
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
import edx.connector.persistence.EdxCpoAssetMapping;
import edx.connector.persistence.EdxCpoAssetMappingRepository;

@Configuration
@ComponentScan(
    basePackageClasses = EdxConnectorAutoConfiguration.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = EdxEnrichedCdrController.class
    )
)
@EntityScan(basePackageClasses = { EdxCdrIngestMapping.class, EdxCpoAssetMapping.class })
@EnableJpaRepositories(basePackageClasses = { EdxCdrIngestMappingRepository.class, EdxCpoAssetMappingRepository.class })
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
        edx.connector.persistence.CdrIngestMappingStore mappingStore,
        org.springframework.beans.factory.ObjectProvider<CpoAssetProvisioningService> assetProvisioningService
    ) {
        return new CdrForwarder(
            cdrServiceClient,
            mappingStore,
            assetProvisioningService.getIfAvailable()
        );
    }

    @Bean
    public IngestedCdrLookup ingestedCdrLookup(
        CdrServiceClient cdrServiceClient,
        edx.connector.persistence.CdrIngestMappingStore mappingStore
    ) {
        return new IngestedCdrLookup(cdrServiceClient, mappingStore);
    }

    @Bean
    @ConditionalOnProperty(prefix = "edx.edc.management", name = "enabled", havingValue = "true")
    public EdcManagementClient edcManagementClient(Environment environment, HttpClientComponent httpClientComponent) {
        URI baseUri = URI.create(readRequiredString(
            environment,
            "edx.edc.management.baseUrl",
            "EDX_EDC_MANAGEMENT_BASE_URL"
        ));
        String apiKey = readString(environment, "edx.edc.management.apiKey", "EDX_EDC_MANAGEMENT_API_KEY", "");
        int timeoutMs = readInt(
            environment,
            "edx.edc.management.timeoutMs",
            "EDX_EDC_MANAGEMENT_TIMEOUT_MS",
            DEFAULT_TIMEOUT_MS
        );
        LOGGER.info("EDX EDC management client enabled; baseUrl=" + baseUri);
        return new EdcManagementClient(
            baseUri,
            apiKey,
            Duration.ofMillis(timeoutMs),
            cdrObjectMapper(httpClientComponent)
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "edx.edc.management", name = "enabled", havingValue = "true")
    public EdcAssetSettings edcAssetSettings(Environment environment, HttpClientComponent httpClientComponent) {
        String assetPrefix = readString(environment, "edx.edc.asset.prefix", "EDX_EDC_ASSET_PREFIX", "cdr-data");
        String co2RelevantCdrUrl = readString(
            environment,
            "edx.edc.asset.co2RelevantCdrUrl",
            "EDX_EDC_ASSET_CO2_RELEVANT_CDR_URL",
            ""
        );
        if (co2RelevantCdrUrl.isBlank()) {
            co2RelevantCdrUrl = defaultCo2RelevantCdrUrl(
                readRequiredString(environment, "edx.cdr.service.baseUrl", "EDX_CDR_SERVICE_BASE_URL")
            );
        }
        String cdrServiceApiKey = readString(environment, "edx.cdr.service.apiKey", "EDX_CDR_SERVICE_API_KEY", "");
        ObjectMapper mapper = cdrObjectMapper(httpClientComponent);
        List<PolicyConsumerSubject> defaultConsumers = readDefaultConsumers(environment, mapper);
        return new EdcAssetSettings(assetPrefix, co2RelevantCdrUrl, cdrServiceApiKey, defaultConsumers);
    }

    @Bean
    @ConditionalOnProperty(prefix = "edx.edc.management", name = "enabled", havingValue = "true")
    public CpoAssetProvisioningService cpoAssetProvisioningService(
        EdcManagementClient edcManagementClient,
        edx.connector.persistence.CpoAssetMappingStore mappingStore,
        HttpClientComponent httpClientComponent,
        EdcAssetSettings edcAssetSettings
    ) {
        return new CpoAssetProvisioningService(
            edcManagementClient,
            mappingStore,
            cdrObjectMapper(httpClientComponent),
            edcAssetSettings
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "edx.edc.management", name = "enabled", havingValue = "true")
    public CpoPolicyUpdateService cpoPolicyUpdateService(
        EdcManagementClient edcManagementClient,
        edx.connector.persistence.CpoAssetMappingStore mappingStore,
        CpoAssetProvisioningService cpoAssetProvisioningService,
        HttpClientComponent httpClientComponent
    ) {
        return new CpoPolicyUpdateService(
            edcManagementClient,
            mappingStore,
            cpoAssetProvisioningService,
            cdrObjectMapper(httpClientComponent)
        );
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
        if (httpClientComponent == null) {
            return new ObjectMapper();
        }
        try {
            return httpClientComponent.getMapper();
        } catch (Exception e) {
            LOGGER.warning("Unable to use node ObjectMapper; falling back to default mapper: " + e.getMessage());
            return new ObjectMapper();
        }
    }

    static String defaultCo2RelevantCdrUrl(String cdrServiceBaseUrl) {
        String base = cdrServiceBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/api")) {
            return base + "/v1/co2-relevant-cdr";
        }
        return base + CdrServicePaths.API_V1 + "/co2-relevant-cdr";
    }

    private static List<PolicyConsumerSubject> readDefaultConsumers(Environment environment, ObjectMapper mapper) {
        String json = readString(
            environment,
            "edx.edc.asset.defaultConsumersJson",
            "EDX_EDC_ASSET_DEFAULT_CONSUMERS_JSON",
            ""
        );
        if (json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Invalid edx.edc.asset.defaultConsumersJson", e);
        }
    }
}
