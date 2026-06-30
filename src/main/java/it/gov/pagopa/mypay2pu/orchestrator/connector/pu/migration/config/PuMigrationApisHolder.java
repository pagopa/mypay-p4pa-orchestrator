package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config;

import it.gov.pagopa.mypay2pu.orchestrator.config.rest.RestTemplateConfig;
import it.gov.pagopa.pu.migration.controller.generated.MigrationFileApi;
import it.gov.pagopa.pu.migration.generated.ApiClient;
import it.gov.pagopa.pu.migration.generated.BaseApi;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PuMigrationApisHolder {

    private final MigrationFileApi migrationFileApi;

    private final ThreadLocal<String> bearerTokenHolder = new ThreadLocal<>();

    public PuMigrationApisHolder(
            PuMigrationApiClientConfig clientConfig,
            RestTemplateBuilder restTemplateBuilder
    ) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(clientConfig.getBaseUrl());
        apiClient.setBearerToken(bearerTokenHolder::get);
        apiClient.setMaxAttemptsForRetry(Math.max(1, clientConfig.getMaxAttempts()));
        apiClient.setWaitTimeMillis(clientConfig.getWaitTimeMillis());
        if (clientConfig.isPrintBodyWhenError()) {
            restTemplate.setErrorHandler(RestTemplateConfig.bodyPrinterWhenError("PU-MIGRATION"));
        }

        this.migrationFileApi = new MigrationFileApi(apiClient);
    }

    @PreDestroy
    public void unload(){
        bearerTokenHolder.remove();
    }

    /** It will return a {@link MigrationFileApi} instrumented with the provided accessToken. Use null if auth is not required */
    public MigrationFileApi getMigrationFileApi(String accessToken){
        return getApi(accessToken, migrationFileApi);
    }

    private <T extends BaseApi> T getApi(String accessToken, T api) {
        bearerTokenHolder.set(accessToken);
        return api;
    }
}
