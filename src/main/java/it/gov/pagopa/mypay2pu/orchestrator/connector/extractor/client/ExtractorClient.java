package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config.ExtractorApisHolder;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import org.springframework.stereotype.Service;

@Service
public class ExtractorClient {

  private final ExtractorApisHolder extractorApisHolder;

  public ExtractorClient(ExtractorApisHolder extractorApisHolder) {
    this.extractorApisHolder = extractorApisHolder;
  }

  public ExtractionAcceptedResponse createExtraction(ExtractionRequest extractionRequest) {
    return extractorApisHolder.getExtractApi().createExtraction(extractionRequest);
  }

  public ExtractionStatusResponse getExtractionStatus(String extractionId) {
    return extractorApisHolder.getExtractApi().getExtractionStatus(extractionId);
  }
}
