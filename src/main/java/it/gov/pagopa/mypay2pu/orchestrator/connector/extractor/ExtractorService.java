package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;

public interface ExtractorService {
  ExtractionAcceptedResponse createExtraction(ExtractionRequest extractionRequest);
  ExtractionStatusResponse getExtractionStatus(String extractionId);
}
