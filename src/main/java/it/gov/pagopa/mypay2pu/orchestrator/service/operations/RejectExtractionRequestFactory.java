package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionFilters;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class RejectExtractionRequestFactory {

  /**
   * Builds a request for one type and IPA; the logical key must already be formatted and validated.
   */
  // TODO: invoke for each type/IPA group using the reject logical-key formatter.
  public ExtractionRequest create(MigrationFileType fileType, String ipaCode, String logicalKey) {
    if (fileType == null || !StringUtils.hasText(ipaCode)) {
      throw invalidRequest("File type and IPA code are required");
    }

    boolean requiresKey = switch (fileType) {
      case ORGANIZATIONS, ORG_SIL_SERVICES -> false;
      case DEBT_POSITIONS_TYPE, DEBT_POSITIONS_TYPE_ORG, DEBT_POSITIONS_TYPE_ORG_OPERATORS,
           DEBT_POSITIONS, DEBT_POSITIONS_PAID, PAYMENT_NOTIFICATION, PAYMENTS_REPORTING_MYPAY,
           TREASURY_CSV_COMPLETE, ASSESSMENTS, ASSESSMENTS_REGISTRY -> true;
      case PAYMENTS_REPORTING_MYPIVOT -> throw invalidRequest(
        "REJECTS_ONLY logical key is not specified for " + fileType);
    };

    if (requiresKey != StringUtils.hasText(logicalKey) || (!requiresKey && logicalKey != null)) {
      throw invalidRequest(requiresKey
        ? "A logical key is required for " + fileType
        : "A logical key must not be provided for " + fileType);
    }

    ExtractionFilters filters = requiresKey ? new ExtractionFilters(null, null, logicalKey) : null;
    return new ExtractionRequest(List.of(ipaCode), fileType, null, filters);
  }

  private InvalidValueException invalidRequest(String message) {
    return new InvalidValueException("INVALID_REJECT_EXTRACTION", message);
  }
}
