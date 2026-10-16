package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.UUID;

/** Validates migration requests and resolves referenced migration executions. */
@Component
public class MigrationRequestValidator {

  private final MigrationExecutionRepository migrationExecutionRepository;

  public MigrationRequestValidator(MigrationExecutionRepository migrationExecutionRepository) {
    this.migrationExecutionRepository = migrationExecutionRepository;
  }

  public void validate(MigrationRequestDto request) {
    if (request.getAction() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "action is required");
    }
    if (request.getOrchestationMode() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "orchestationMode is required");
    }
    validateAction(request);
    validateCycleMode(request);
  }

  public MigrationExecution requireTransferExecution(MigrationRequestDto request) {
    UUID migrationId = request.getMigrationId();
    if (migrationId == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required for TRANSFER");
    }
    return requireExistingMigration(migrationId);
  }

  public MigrationExecution requirePreviousMigration(MigrationRequestDto request) {
    UUID migrationId = request.getMigrationId();
    if (migrationId == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required when orchestationMode is REJECTS_ONLY");
    }
    return requireExistingMigration(migrationId);
  }

  private MigrationExecution requireExistingMigration(UUID migrationId) {
    return migrationExecutionRepository.findById(migrationId)
      .orElseThrow(() -> new InvalidValueException(
        "INVALID_MIGRATION_REQUEST",
        "migrationId does not identify an existing migration"
      ));
  }

  protected void validateAction(MigrationRequestDto request) {
    switch (request.getAction()) {
      case EXPORT, COMPLETE -> {
        if (CollectionUtils.isEmpty(request.getIpaCodes())) {
          throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "ipaCodes are required and must not be empty");
        }
        if (CollectionUtils.isEmpty(request.getFileTypesToInclude())) {
          throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "fileTypesToInclude are required and must not be empty");
        }
        if (request.getOrchestationMode() == CycleMode.REJECTS_ONLY && request.getMigrationId() == null) {
          throw new InvalidValueException(
            "INVALID_MIGRATION_REQUEST",
            "migrationId is required when orchestationMode is REJECTS_ONLY"
          );
        }
      }
      case TRANSFER -> {
        if (request.getOrchestationMode() == CycleMode.REJECTS_ONLY) {
          throw new InvalidValueException(
            "INVALID_MIGRATION_REQUEST",
            "orchestationMode REJECTS_ONLY is not supported for TRANSFER"
          );
        }
        if (request.getMigrationId() == null) {
          throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required for TRANSFER");
        }
      }
    }
  }

  protected void validateCycleMode(MigrationRequestDto request) {
    if (request.getAction() == MigrationAction.TRANSFER) {
      return;
    }
    if (request.getOrchestationMode() == CycleMode.FULL && request.getPeriod() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "period is required when orchestationMode is FULL");
    }
  }
}
