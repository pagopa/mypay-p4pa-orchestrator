package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.client;

import it.gov.pagopa.mypay2pu.extractor.controller.generated.ExtractApi;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config.ExtractorApisHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractorClientTest {

  private static final String ACCESS_TOKEN = "access-token";

  @Mock
  private ExtractorApisHolder extractorApisHolderMock;
  @Mock
  private ExtractApi extractApiMock;

  private ExtractorClient extractorClient;

  @BeforeEach
  void setUp() {
    when(extractorApisHolderMock.getExtractApi(ACCESS_TOKEN)).thenReturn(extractApiMock);
    extractorClient = new ExtractorClient(extractorApisHolderMock);
  }

  @AfterEach
  void verifyNoUnexpectedInteractions() {
    verifyNoMoreInteractions(extractorApisHolderMock, extractApiMock);
  }

  @Test
  void givenExtractionRequestWhenCreateExtractionThenReturnsGeneratedResponse() {
    ExtractionRequest extractionRequest = new ExtractionRequest();
    ExtractionAcceptedResponse expectedResponse = new ExtractionAcceptedResponse("extraction-123");
    when(extractApiMock.createExtraction(extractionRequest)).thenReturn(expectedResponse);

    ExtractionAcceptedResponse response = extractorClient.createExtraction(ACCESS_TOKEN, extractionRequest);

    assertSame(expectedResponse, response);
  }

  @Test
  void givenExtractionIdWhenGetExtractionStatusThenReturnsGeneratedResponse() {
    String extractionId = "extraction-123";
    ExtractionStatusResponse expectedResponse = new ExtractionStatusResponse();
    when(extractApiMock.getExtractionStatus(extractionId)).thenReturn(expectedResponse);

    ExtractionStatusResponse response = extractorClient.getExtractionStatus(ACCESS_TOKEN, extractionId);

    assertSame(expectedResponse, response);
  }

  @Test
  void givenApiFailureWhenCreateExtractionThenPropagatesTheFailure() {
    ExtractionRequest extractionRequest = new ExtractionRequest();
    RestClientException expectedException = new RestClientException("Extractor is unavailable");
    when(extractApiMock.createExtraction(extractionRequest)).thenThrow(expectedException);

    RestClientException exception = assertThrows(
      RestClientException.class,
      () -> extractorClient.createExtraction(ACCESS_TOKEN, extractionRequest)
    );

    assertSame(expectedException, exception);
  }
}
