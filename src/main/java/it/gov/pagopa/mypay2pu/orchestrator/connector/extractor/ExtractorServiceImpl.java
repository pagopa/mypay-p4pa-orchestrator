package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.client.ExtractorClient;
import org.springframework.stereotype.Service;

@Service
public class ExtractorServiceImpl implements ExtractorService {
  private final ExtractorClient extractorClient;

  public ExtractorServiceImpl(ExtractorClient extractorClient) {
    this.extractorClient = extractorClient;
  }

  @Override
  public ExtractionAcceptedResponse createExtraction(ExtractionRequest extractionRequest) {
    return extractorClient.createExtraction(extractionRequest);
  }

  @Override
  public ExtractionStatusResponse getExtractionStatus(String extractionId) {
    return extractorClient.getExtractionStatus(extractionId);
  }
}
