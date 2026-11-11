package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config.ExtractorApisHolder;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class ExtractorClient {

  private final ExtractorApisHolder extractorApisHolder;
  private final JsonMapper jsonMapper;

  public ExtractorClient(ExtractorApisHolder extractorApisHolder, JsonMapper jsonMapper) {
    this.extractorApisHolder = extractorApisHolder;
    this.jsonMapper = jsonMapper;
  }

  public ExtractionAcceptedResponse createExtraction(ExtractionRequest extractionRequest) {
    return extractorApisHolder.getExtractApi().createExtraction(extractionRequest);
  }

  public ExtractionStatusResponse getExtractionStatus(String extractionId) {
    try {
      return extractorApisHolder.getExtractApi().getExtractionStatus(extractionId);
    } catch (HttpClientErrorException.NotFound failure) {
      throw transcodeNotFound(extractionId, failure);
    }
  }

  /**
   * Converts an Extractor 404 into a business exception without hiding the HTTP cause.
   *
   * @param extractionId extraction identifier used in diagnostic logs
   * @param failure original HTTP error
   * @return the business error with the remote code and message when available
   */
  private NotFoundException transcodeNotFound(String extractionId, HttpClientErrorException.NotFound failure) {
    String code = "EXTRACTOR_NOT_FOUND";
    String message = failure.getMessage();
    try {
      var body = jsonMapper.readTree(failure.getResponseBodyAsString());
      String remoteCode = body.path("code").asString();
      String remoteMessage = body.path("message").asString();
      if (StringUtils.hasText(remoteCode)) {
        code = remoteCode;
      }
      if (StringUtils.hasText(remoteMessage)) {
        message = remoteMessage;
      }
    } catch (JacksonException invalidBody) {
      log.warn("Cannot decode Extractor 404 response for extraction {}", extractionId, invalidBody);
    }
    log.info("Cannot find extraction status for id {}: code={}", extractionId, code);
    return new NotFoundException(code, message, failure);
  }
}
