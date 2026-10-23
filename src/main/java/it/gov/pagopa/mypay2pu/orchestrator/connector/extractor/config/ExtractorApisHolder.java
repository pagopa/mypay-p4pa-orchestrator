package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ErrorDTO;
import it.gov.pagopa.mypay2pu.orchestrator.config.rest.HttpClientErrorJsonBodyHandler;
import it.gov.pagopa.mypay2pu.extractor.controller.generated.ExtractApi;
import it.gov.pagopa.mypay2pu.extractor.generated.ApiClient;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ExtractorApisHolder {
  private final ExtractorApiClientConfig clientConfig;
  private final RestTemplate restTemplate;
  private final ExtractApi extractApi;

  public ExtractorApisHolder(ExtractorApiClientConfig clientConfig,
                             RestTemplateBuilder restTemplateBuilder,
                             JsonMapper jsonMapper) {
    this.clientConfig = clientConfig;
    this.restTemplate = restTemplateBuilder.build();

    ApiClient apiClient = new ApiClient(restTemplate);
    apiClient.setBasePath(clientConfig.getBaseUrl());
    apiClient.setUsername(clientConfig.getUsername());
    apiClient.setPassword(clientConfig.getPassword());
    apiClient.setMaxAttemptsForRetry(Math.max(1, clientConfig.getMaxAttempts()));
    apiClient.setWaitTimeMillis(clientConfig.getWaitTimeMillis());

    restTemplate.setErrorHandler(new HttpClientErrorJsonBodyHandler<>(
      jsonMapper,
      "EXTRACTOR",
      clientConfig.isPrintBodyWhenError(),
      ErrorDTO.class,
      errorDTO -> errorDTO.getCategory().getValue(),
      ErrorDTO::getMessage
    ));
    this.extractApi = new ExtractApi(apiClient);
  }

  public ExtractApi getExtractApi() {
    return extractApi;
  }
}
