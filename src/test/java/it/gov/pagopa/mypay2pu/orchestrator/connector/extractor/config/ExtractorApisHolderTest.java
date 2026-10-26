package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.orchestrator.config.BasicAuthProperties;
import it.gov.pagopa.mypay2pu.orchestrator.config.json.JsonConfig;
import it.gov.pagopa.mypay2pu.orchestrator.connector.BaseApiHolderTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.List;

import static it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType.ORGANIZATIONS;

@ExtendWith(MockitoExtension.class)
class ExtractorApisHolderTest extends BaseApiHolderTest {

  @Mock
  private RestTemplateBuilder restTemplateBuilderMock;

  private ExtractorApisHolder extractorApisHolder;
  private ExtractorApiClientConfig apiClientConfig;

  @BeforeEach
  void setUp() {
    Mockito.when(restTemplateBuilderMock.build()).thenReturn(restTemplateMock);
    Mockito.when(restTemplateMock.getUriTemplateHandler()).thenReturn(new DefaultUriBuilderFactory());

    apiClientConfig = ExtractorApiClientConfig.builder()
      .baseUrl("http://example.com")
      .maxAttempts(3)
      .build();
    BasicAuthProperties basicAuth = new BasicAuthProperties("test-user", "test-password");

    extractorApisHolder = new ExtractorApisHolder(apiClientConfig, basicAuth, restTemplateBuilderMock, new JsonConfig().objectMapperJackson3());
    verifyHttpClientErrorJsonBodyHandlerConfiguration(extractorApisHolder.getExtractApi());
  }

  @AfterEach
  void verifyNoMoreInteractions() {
    Mockito.verifyNoMoreInteractions(restTemplateBuilderMock, restTemplateMock);
  }

  @Test
  void givenRetryConfigurationWhenCreateExtractionThenRetriesTransientErrors() {
    assertRetry(apiClientConfig,
      ignored -> extractorApisHolder.getExtractApi()
        .createExtraction(new ExtractionRequest().ipaCodes(List.of("IPACODE")).fileTypes(ORGANIZATIONS)),
      new ParameterizedTypeReference<>() {}
    );
  }

  @Test
  void givenBasicCredentialsWhenCreateExtractionThenAuthenticationIsThreadSafe() throws InterruptedException {
    assertAuthenticationShouldBeSetInThreadSafeMode(
      basic -> extractorApisHolder.getExtractApi()
        .createExtraction(new ExtractionRequest().ipaCodes(List.of("IPACODE")).fileTypes(ORGANIZATIONS)),
      new ParameterizedTypeReference<>() {},
      null,
      AUTH_TYPE.BASIC
    );
  }
}
