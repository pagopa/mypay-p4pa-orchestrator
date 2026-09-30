package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * Validates migration requests before they are delegated to the application service.
 */
@Component
public class MigrationRequestValidator {

  public void validate(MigrationRequestDto request) {
    if (request != null) {
      validatePhase(request);
      validateCycleMode(request);
    }
  }

  protected void validatePhase(MigrationRequestDto request) {
    MigrationPhase phase = request.getPhase();
    if (phase == MigrationPhase.EXPORT || phase == MigrationPhase.ALL) {
      if (!StringUtils.hasText((CharSequence) request.getIpaCodes())) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "ipaCodes are required and must not be empty");
      }
      if (!StringUtils.hasText(Objects.requireNonNull(request.getCycleMode()).toString())) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "cycleMode is required");
      }
      if (!StringUtils.hasText(request.getFileTypesToInclude().toString())) {
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
      JsonNullable<Map<String, Object>> rejectsPayload = request.getRejectsPayload();
      if (rejectsPayload == null || !rejectsPayload.isPresent() || rejectsPayload.get() == null) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "rejectsPayload is required when cycleMode is REJECTS_ONLY");
      }
    }
  }
}
