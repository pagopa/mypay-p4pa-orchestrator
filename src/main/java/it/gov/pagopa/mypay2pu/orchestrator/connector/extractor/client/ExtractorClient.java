package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config.ExtractorApisHolder;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Slf4j
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
    try {
      return extractorApisHolder.getExtractApi().getExtractionStatus(extractionId);
    } catch (NotFoundException e) {
      log.info("Cannot find extraction status for id {}", extractionId);
      return null;
    }
  }
}
