package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.config;

import it.gov.pagopa.mypay2pu.orchestrator.config.rest.RestTemplateConfig;
import it.gov.pagopa.pu.auth.controller.generated.AuthnApi;
import it.gov.pagopa.pu.auth.generated.ApiClient;
import it.gov.pagopa.pu.auth.generated.BaseApi;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PuAuthApisHolder {

    private final AuthnApi authnApi;

    private final ThreadLocal<String> bearerTokenHolder = new ThreadLocal<>();

    public PuAuthApisHolder(
            PuAuthApiClientConfig clientConfig,
            RestTemplateBuilder restTemplateBuilder
    ) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(clientConfig.getBaseUrl());
        apiClient.setBearerToken(bearerTokenHolder::get);
        apiClient.setMaxAttemptsForRetry(Math.max(1, clientConfig.getMaxAttempts()));
        apiClient.setWaitTimeMillis(clientConfig.getWaitTimeMillis());
        if (clientConfig.isPrintBodyWhenError()) {
            restTemplate.setErrorHandler(RestTemplateConfig.bodyPrinterWhenError("PU-AUTH"));
        }

        this.authnApi = new AuthnApi(apiClient);
    }

    @PreDestroy
    public void unload(){
        bearerTokenHolder.remove();
    }

    /** It will return a {@link AuthnApi} instrumented with the provided accessToken. Use null if auth is not required */
    public AuthnApi getAuthnApi(String accessToken){
        return getApi(accessToken, authnApi);
    }

    private <T extends BaseApi> T getApi(String accessToken, T api) {
        bearerTokenHolder.set(accessToken);
        return api;
    }
}
