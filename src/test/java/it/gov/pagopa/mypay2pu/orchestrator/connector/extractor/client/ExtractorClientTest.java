package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.client;

import it.gov.pagopa.mypay2pu.extractor.controller.generated.ExtractApi;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config.ExtractorApisHolder;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractorClientTest {

  @Mock
  private ExtractorApisHolder extractorApisHolderMock;
  @Mock
  private ExtractApi extractApiMock;

  private ExtractorClient extractorClient;

  @BeforeEach
  void setUp() {
    when(extractorApisHolderMock.getExtractApi()).thenReturn(extractApiMock);
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

    ExtractionAcceptedResponse response = extractorClient.createExtraction(extractionRequest);

    assertSame(expectedResponse, response);
  }

  @ParameterizedTest
  @ValueSource(strings = {"extraction-123", "not-found-extraction"})
  void givenExtractionIdWhenGetExtractionStatusThenInvokeApi(String extractionId) {
    ExtractionStatusResponse expectedResponse;

    if ("not-found-extraction".equals(extractionId)) {
      when(extractApiMock.getExtractionStatus(extractionId)).thenThrow(new NotFoundException("ERRORCODE", "ERRORMESSAGE"));
      expectedResponse = null;
    } else {
      expectedResponse = new ExtractionStatusResponse();
      when(extractApiMock.getExtractionStatus(extractionId)).thenReturn(expectedResponse);
    }

    ExtractionStatusResponse response = extractorClient.getExtractionStatus(extractionId);

    assertEquals(expectedResponse, response);
  }

  @Test
  void givenApiFailureWhenCreateExtractionThenPropagatesTheFailure() {
    ExtractionRequest extractionRequest = new ExtractionRequest();
    RestClientException expectedException = new RestClientException("Extractor is unavailable");
    when(extractApiMock.createExtraction(extractionRequest)).thenThrow(expectedException);

    RestClientException exception = assertThrows(
      RestClientException.class,
      () -> extractorClient.createExtraction(extractionRequest)
    );

    assertSame(expectedException, exception);
  }
}
