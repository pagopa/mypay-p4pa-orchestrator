package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Validates migration requests before they are delegated to the application service.
 */
@Component
public class MigrationRequestValidator {

  public void validate(MigrationRequestDto request) {
    validatePhase(request);
    validateCycleMode(request);
  }

  protected void validatePhase(MigrationRequestDto request) {
    MigrationPhase phase = request.getPhase();
    if (phase == MigrationPhase.EXPORT || phase == MigrationPhase.ALL) {
      if (CollectionUtils.isEmpty(request.getIpaCodes())) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "ipaCodes are required and must not be empty");
      }
      if (CollectionUtils.isEmpty(request.getFileTypesToInclude())) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "fileTypesToInclude are required and must not be empty");
      }
    } else if (phase == MigrationPhase.TRANSFER && request.getMigrationId() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required for TRANSFER");
    }
  }

  protected void validateCycleMode(MigrationRequestDto request) {
    if (request.getCycleMode() == CycleMode.FULL && request.getPeriod() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "period is required when cycleMode is FULL");
    }
    if (request.getCycleMode() == CycleMode.REJECTS_ONLY) {
      List<String> rejectsPayload = request.getRejectsPayload();
      if (CollectionUtils.isEmpty(rejectsPayload)) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "rejectsPayload is required when cycleMode is REJECTS_ONLY");
      }
    }
  }
}
