package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.client.ExtractorClient;
import it.gov.pagopa.mypay2pu.orchestrator.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.jemos.podam.api.PodamFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractorServiceImplTest {
  private final PodamFactory podamFactory = TestUtils.getPodamFactory();

  @Mock
  private ExtractorClient extractorClientMock;

  private ExtractorService service;

  @BeforeEach
  void setUp() {
    service = new ExtractorServiceImpl(extractorClientMock);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(extractorClientMock);
  }

  @Test
  void whenClientCreateExtractionThenAcceptExtractionResponse() {
    // Given
    ExtractionRequest extractionRequest = podamFactory.manufacturePojo(ExtractionRequest.class);
    ExtractionAcceptedResponse expectedResponse = podamFactory.manufacturePojo(ExtractionAcceptedResponse.class);

    when(extractorClientMock.createExtraction(extractionRequest)).thenReturn(expectedResponse);

    // When
    ExtractionAcceptedResponse result = service.createExtraction(extractionRequest);

    // Then
    assertEquals(expectedResponse, result);
  }

  @Test
  void whenClientGetExtractionStatusThenReturnExtractionStatusResponse() {
    // Given
    String extractionId = "extractionId";
    ExtractionStatusResponse expectedResponse = podamFactory.manufacturePojo(ExtractionStatusResponse.class);

    when(extractorClientMock.getExtractionStatus(extractionId)).thenReturn(expectedResponse);

    // When
    ExtractionStatusResponse result = service.getExtractionStatus(extractionId);

    // Then
    assertEquals(expectedResponse, result);
  }
}
