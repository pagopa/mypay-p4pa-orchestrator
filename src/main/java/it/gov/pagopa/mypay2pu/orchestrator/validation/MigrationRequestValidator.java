package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Validates migration requests and resolves referenced migration executions. */
@Component
public class MigrationRequestValidator {

  private final MigrationExecutionRepository migrationExecutionRepository;
  private final MigrationFileExecutionRepository migrationFileExecutionRepository;

  private static final Set<MigrationFileType> DELTA_TYPES = Set.of(
    MigrationFileType.DEBT_POSITIONS,
    MigrationFileType.DEBT_POSITIONS_PAID,
    MigrationFileType.PAYMENT_NOTIFICATION,
    MigrationFileType.PAYMENTS_REPORTING_MYPAY,
    MigrationFileType.PAYMENTS_REPORTING_MYPIVOT,
    MigrationFileType.TREASURY_CSV_COMPLETE,
    MigrationFileType.ASSESSMENTS,
    MigrationFileType.ASSESSMENTS_REGISTRY
  );

  public MigrationRequestValidator(MigrationExecutionRepository migrationExecutionRepository,
                                   MigrationFileExecutionRepository migrationFileExecutionRepository) {
    this.migrationExecutionRepository = migrationExecutionRepository;
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
  }

  public void validate(MigrationRequestDto request) {
    if (request.getAction() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "action is required");
    }
    if (request.getOrchestrationMode() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "orchestrationMode is required");
    }
    validateAction(request);
    validateOrchestrationMode(request);
  }

  public MigrationExecution requireTransferExecution(MigrationRequestDto request) {
    UUID migrationId = request.getMigrationId();
    if (migrationId == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required for TRANSFER");
    }
    MigrationExecution execution = requireExistingMigration(migrationId);
    if (execution.getStatus() != MigrationExecutionStatus.COMPLETED
      || execution.getAction() != MigrationAction.EXPORT) {
      throw new InvalidValueException(
        "INVALID_MIGRATION_REQUEST",
        "migrationId must identify a completed EXPORT migration for TRANSFER"
      );
    }
    return execution;
  }

  public MigrationExecution requirePreviousMigration(MigrationRequestDto request) {
    UUID migrationId = request.getMigrationId();
    if (migrationId == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required when orchestrationMode is REJECTS_ONLY");
    }
    return requireExistingMigration(migrationId);
  }

  public MigrationExecution requireDeltaPreviousMigration(MigrationRequestDto request) {
    MigrationExecution previous = requireExistingMigration(request.getMigrationId());
    if (!isSuccessfulExtraction(previous) || !hasMatchingDeltaScope(previous, request)) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
        "migrationId must identify a completed extraction with a checkpoint and matching IPA codes and file types");
    }
    validateDeltaPreviousFiles(previous.getId(), request.getFileTypesToInclude());
    return previous;
  }

  private boolean isSuccessfulExtraction(MigrationExecution execution) {
    return execution.getStatus() == MigrationExecutionStatus.COMPLETED
      && execution.getOrchestrationMode() != OrchestrationMode.REJECTS_ONLY
      && execution.getLastSuccessfulExtractionAt() != null;
  }

  private boolean hasMatchingDeltaScope(MigrationExecution previous, MigrationRequestDto request) {
    return previous.getIpaCodes() != null
      && previous.getIpaCodes().stream().noneMatch(ObjectUtils::isEmpty)
      && new HashSet<>(previous.getIpaCodes()).equals(new HashSet<>(request.getIpaCodes()))
      && previous.getFileTypes() != null
      && previous.getFileTypes().containsAll(request.getFileTypesToInclude());
  }

  private void validateDeltaPreviousFiles(UUID migrationId, List<MigrationFileType> requestedTypes) {
    Map<MigrationFileType, List<MigrationFileExecution>> filesByType = migrationFileExecutionRepository
      .findAllByMigrationIdOrderByCreationDateAscIdAsc(migrationId)
      .stream()
      .collect(Collectors.groupingBy(MigrationFileExecution::getFileType));
    for (MigrationFileType type : requestedTypes) {
      List<MigrationFileExecution> matching = filesByType.getOrDefault(type, List.of());
      if (matching.size() != 1 || (matching.getFirst().getStatus() != MigrationFileExecutionStatus.EXPORTED
        && matching.getFirst().getStatus() != MigrationFileExecutionStatus.COMPLETED)) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
          "previous extraction must have one successful file execution for every requested file type");
      }
    }
  }

  private MigrationExecution requireExistingMigration(UUID migrationId) {
    return migrationExecutionRepository.findById(migrationId)
      .orElseThrow(() -> new InvalidValueException(
        "INVALID_MIGRATION_REQUEST",
        "migrationId does not identify an existing migration"
      ));
  }

  protected void validateAction(MigrationRequestDto request) {
    validateExclusivePaymentsReportingFileTypes(request);
    switch (request.getAction()) {
      case EXPORT, COMPLETE -> {
        if (CollectionUtils.isEmpty(request.getIpaCodes())) {
          throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "ipaCodes are required and must not be empty");
        }
        if (CollectionUtils.isEmpty(request.getFileTypesToInclude())) {
          throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "fileTypesToInclude are required and must not be empty");
        }
        if (request.getOrchestrationMode() == OrchestrationMode.REJECTS_ONLY && request.getMigrationId() == null) {
          throw new InvalidValueException(
            "INVALID_MIGRATION_REQUEST",
            "migrationId is required when orchestrationMode is REJECTS_ONLY"
          );
        }
      }
      case TRANSFER -> {
        if (request.getOrchestrationMode() == OrchestrationMode.REJECTS_ONLY) {
          throw new InvalidValueException(
            "INVALID_MIGRATION_REQUEST",
            "orchestrationMode REJECTS_ONLY is not supported for TRANSFER"
          );
        }
        if (request.getMigrationId() == null) {
          throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required for TRANSFER");
        }
      }
    }
  }

  private void validateExclusivePaymentsReportingFileTypes(MigrationRequestDto request) {
    if (!CollectionUtils.isEmpty(request.getFileTypesToInclude())
      && request.getFileTypesToInclude().contains(MigrationFileType.PAYMENTS_REPORTING_MYPAY)
      && request.getFileTypesToInclude().contains(MigrationFileType.PAYMENTS_REPORTING_MYPIVOT)) {
      throw new InvalidValueException(
        "INVALID_MIGRATION_REQUEST",
        "fileTypesToInclude must not contain both PAYMENTS_REPORTING_MYPAY and PAYMENTS_REPORTING_MYPIVOT"
      );
    }
  }

  protected void validateOrchestrationMode(MigrationRequestDto request) {
    if (request.getAction() == MigrationAction.TRANSFER) {
      return;
    }
    if (request.getOrchestrationMode() == OrchestrationMode.REJECTS_ONLY) {
      requirePreviousMigration(request);
      throw new InvalidValueException(
        "INVALID_MIGRATION_REQUEST",
        "orchestrationMode REJECTS_ONLY is unavailable because discard-key data is not yet available"
      );
    }
    if (request.getOrchestrationMode() == OrchestrationMode.DELTA) {
      validateDeltaRequest(request);
    }
    if (request.getOrchestrationMode() == OrchestrationMode.FULL) {
      PeriodDto period = request.getPeriod();
      if (period == null) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "period is required when orchestrationMode is FULL");
      }
      OffsetDateTime fromDate = period.getFromDate();
      OffsetDateTime toDate = period.getToDate();
      if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
        throw new InvalidValueException(
          "INVALID_MIGRATION_REQUEST",
          "period.fromDate must not be after period.toDate"
        );
      }
    }
  }

  private void validateDeltaRequest(MigrationRequestDto request) {
    if (request.getMigrationId() == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required when orchestrationMode is DELTA");
    }
    if (request.getIpaCodes().stream().anyMatch(ipa -> ipa == null || ipa.isBlank())) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "ipaCodes must not contain null or blank values");
    }
    if (request.getFileTypesToInclude().stream().anyMatch(Objects::isNull)
      || !DELTA_TYPES.containsAll(request.getFileTypesToInclude())) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "fileTypesToInclude contains types not supported for DELTA");
    }
  }
}
