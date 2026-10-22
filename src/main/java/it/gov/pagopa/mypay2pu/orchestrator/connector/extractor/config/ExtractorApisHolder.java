package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config;

import it.gov.pagopa.mypay2pu.orchestrator.config.rest.RestTemplateConfig;
import it.gov.pagopa.mypay2pu.extractor.controller.generated.ExtractApi;
import it.gov.pagopa.mypay2pu.extractor.generated.ApiClient;
import it.gov.pagopa.mypay2pu.extractor.generated.BaseApi;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ExtractorApisHolder {

  private final ExtractApi extractApi;
  private final ThreadLocal<String> bearerTokenHolder = new ThreadLocal<>();

  public ExtractorApisHolder(
    ExtractorApiClientConfig clientConfig,
    RestTemplateBuilder restTemplateBuilder
  ) {
    RestTemplate restTemplate = restTemplateBuilder.build();
    ApiClient apiClient = new ApiClient(restTemplate);
    apiClient.setBasePath(clientConfig.getBaseUrl());
    apiClient.setMaxAttemptsForRetry(Math.max(1, clientConfig.getMaxAttempts()));
    apiClient.setWaitTimeMillis(clientConfig.getWaitTimeMillis());
    if (clientConfig.isPrintBodyWhenError()) {
      restTemplate.setErrorHandler(RestTemplateConfig.bodyPrinterWhenError("EXTRACTOR"));
    }

    this.extractApi = new ExtractApi(apiClient);
  }

  @PreDestroy
  public void unload() {
    bearerTokenHolder.remove();
  }

  public ExtractApi getExtractApi(String accessToken) {
    return getApi(accessToken, extractApi);
  }

  private <T extends BaseApi> T getApi(String accessToken, T api) {
    bearerTokenHolder.set(accessToken);
    return api;
  }
}
