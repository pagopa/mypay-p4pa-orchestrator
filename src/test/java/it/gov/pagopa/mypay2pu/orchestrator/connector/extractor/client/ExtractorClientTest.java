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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
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
    extractorClient = new ExtractorClient(extractorApisHolderMock, JsonMapper.builder().build());
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
    verify(extractorApisHolderMock).getExtractApi();
    verify(extractApiMock).createExtraction(extractionRequest);
  }

  @Test
  void givenExtractionIdWhenGetExtractionStatusThenInvokeApi() {
    String extractionId = "extraction-123";
    ExtractionStatusResponse expectedResponse = new ExtractionStatusResponse();
    when(extractApiMock.getExtractionStatus(extractionId)).thenReturn(expectedResponse);

    ExtractionStatusResponse response = extractorClient.getExtractionStatus(extractionId);

    assertEquals(expectedResponse, response);
    verify(extractorApisHolderMock).getExtractApi();
    verify(extractApiMock).getExtractionStatus(extractionId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"EXPORT_FILE_NOT_FOUND", "OTHER_NOT_FOUND"})
  void givenHttpNotFoundWhenGetExtractionStatusThenPreservesBusinessCodeAndCause(String code) {
    String extractionId = "extraction-123";
    HttpClientErrorException failure = notFound("{\"code\":\"" + code + "\",\"message\":\"Not visible\"}");
    when(extractApiMock.getExtractionStatus(extractionId)).thenThrow(failure);

    NotFoundException thrown = assertThrows(NotFoundException.class,
      () -> extractorClient.getExtractionStatus(extractionId));

    assertEquals(code, thrown.getCode());
    assertEquals("Not visible", thrown.getMessage());
    assertSame(failure, thrown.getCause());
    verify(extractorApisHolderMock).getExtractApi();
    verify(extractApiMock).getExtractionStatus(extractionId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not json", "{}", "null", "", "[]", "{\"code\":\" \"}"})
  void givenInvalidNotFoundBodyWhenGetExtractionStatusThenReturnsNonRetryableBusinessError(String body) {
    String extractionId = "extraction-123";
    HttpClientErrorException failure = notFound(body);
    when(extractApiMock.getExtractionStatus(extractionId)).thenThrow(failure);

    NotFoundException thrown = assertThrows(NotFoundException.class,
      () -> extractorClient.getExtractionStatus(extractionId));

    assertEquals("EXTRACTOR_NOT_FOUND", thrown.getCode());
    assertEquals(failure.getMessage(), thrown.getMessage());
    assertSame(failure, thrown.getCause());
    verify(extractorApisHolderMock).getExtractApi();
    verify(extractApiMock).getExtractionStatus(extractionId);
  }

  @Test
  void givenBusinessNotFoundWhenGetExtractionStatusThenPropagatesWithoutReturningNull() {
    String extractionId = "extraction-123";
    NotFoundException failure = new NotFoundException("EXPORT_FILE_NOT_FOUND", "Not visible");
    when(extractApiMock.getExtractionStatus(extractionId)).thenThrow(failure);

    assertSame(failure, assertThrows(NotFoundException.class,
      () -> extractorClient.getExtractionStatus(extractionId)));
    verify(extractorApisHolderMock).getExtractApi();
    verify(extractApiMock).getExtractionStatus(extractionId);
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
    verify(extractorApisHolderMock).getExtractApi();
    verify(extractApiMock).createExtraction(extractionRequest);
  }

  private static HttpClientErrorException notFound(String body) {
    return HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
      body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
  }
}
