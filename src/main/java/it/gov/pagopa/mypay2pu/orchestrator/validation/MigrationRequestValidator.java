package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.UUID;

/** Validates migration requests and resolves referenced migration executions. */
@Component
public class MigrationRequestValidator {

  private final MigrationExecutionRepository migrationExecutionRepository;

  public MigrationRequestValidator(MigrationExecutionRepository migrationExecutionRepository) {
    this.migrationExecutionRepository = migrationExecutionRepository;
  }

  public void validate(MigrationRequestDto request) {
    if (request.getPhase() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "phase is required");
    }
    if (request.getCycleMode() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "cycleMode is required");
    }
    validatePhase(request);
    validateCycleMode(request);
  }

  public MigrationExecution requireTransferExecution(MigrationRequestDto request) {
    UUID migrationId = request.getMigrationId();
    if (migrationId == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required for TRANSFER");
    }
    return migrationExecutionRepository.findById(migrationId)
      .orElseThrow(() -> new InvalidValueException(
        "INVALID_MIGRATION_REQUEST",
        "migrationId does not identify an existing migration"
      ));
  }

  protected void validatePhase(MigrationRequestDto request) {
    MigrationPhase phase = request.getPhase();
    if (phase == MigrationPhase.EXPORT || phase == MigrationPhase.COMPLETE) {
      if (CollectionUtils.isEmpty(request.getIpaCodes())) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "ipaCodes are required and must not be empty");
      }
      if (CollectionUtils.isEmpty(request.getFileTypesToInclude())) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "fileTypesToInclude are required and must not be empty");
      }
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
