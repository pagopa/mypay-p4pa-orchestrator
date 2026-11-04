package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionValidator;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class MigrationFileExecutionService {

  private final MigrationFileExecutionRepository migrationFileExecutionRepository;
  private final MigrationFileExecutionValidator migrationFileExecutionValidator;

  public MigrationFileExecutionService(MigrationFileExecutionRepository migrationFileExecutionRepository,
                                        MigrationFileExecutionValidator migrationFileExecutionValidator) {
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
    this.migrationFileExecutionValidator = migrationFileExecutionValidator;
  }

  /**
   * Ensures a file execution exists for the parent migration and file type.
   * The migration ID does not imply that this file execution has already been created.
   * Existing executions are returned without resetting their status.
   */
  public MigrationFileExecution ensurePending(UUID migrationId, MigrationFileType fileType) {
    return migrationFileExecutionRepository.findByMigrationIdAndFileType(migrationId, fileType)
      .orElseGet(() -> migrationFileExecutionRepository.save(MigrationFileExecution.builder()
        .migrationId(migrationId)
        .fileType(fileType)
        .status(MigrationFileExecutionStatus.PENDING)
        .build()));
  }

  public MigrationFileExecution markExporting(UUID migrationId,
                                              MigrationFileType fileType,
                                              LocalDateTime startedAt) {
    return update(migrationId, fileType, fileExecution -> {
      MigrationFileExecutionStatus targetStatus = migrationFileExecutionValidator.validateMarkExporting(fileExecution);
      fileExecution.setStatus(targetStatus);
      fileExecution.setExtractionStartedAt(startedAt);
    });
  }

  public MigrationFileExecution recordAccepted(UUID migrationId,
                                               MigrationFileType fileType,
                                               String extractionId) {
    migrationFileExecutionValidator.validateAcceptedInput(extractionId);
    return update(migrationId, fileType, fileExecution -> {
      MigrationFileExecutionStatus targetStatus =
        migrationFileExecutionValidator.validateRecordAccepted(fileExecution, extractionId);
      fileExecution.setStatus(targetStatus);
      fileExecution.setExtractionId(extractionId);
    });
  }

  public MigrationFileExecution markExported(UUID migrationId,
                                             MigrationFileType fileType,
                                             String filePath,
                                             LocalDateTime completedAt) {
    return update(migrationId, fileType, fileExecution -> {
      MigrationFileExecutionStatus targetStatus =
        migrationFileExecutionValidator.validateMarkExported(fileExecution, filePath, completedAt);
      fileExecution.setStatus(targetStatus);
      fileExecution.setFilePath(filePath);
      fileExecution.setExtractionCompletedAt(completedAt);
    });
  }

  public MigrationFileExecution markFailed(UUID migrationId,
                                           MigrationFileType fileType,
                                           String errorCode,
                                           String errorMessage) {
    return update(migrationId, fileType, fileExecution -> {
      MigrationFileExecutionStatus targetStatus =
        migrationFileExecutionValidator.validateMarkFailed(fileExecution, errorCode, errorMessage);
      fileExecution.setStatus(targetStatus);
      fileExecution.setExtractionErrorCode(errorCode);
      fileExecution.setExtractionErrorMsg(errorMessage);
    });
  }

  private MigrationFileExecution update(UUID migrationId,
                                        MigrationFileType fileType,
                                        Consumer<MigrationFileExecution> transition) {
    MigrationFileExecution fileExecution = migrationFileExecutionRepository
      .findByMigrationIdAndFileType(migrationId, fileType)
      .orElseThrow(() -> new NotFoundException(
        "MIGRATION_FILE_EXECUTION_NOT_FOUND",
        "File execution does not exist for migration %s and file type %s".formatted(migrationId, fileType)
      ));
    transition.accept(fileExecution);
    return migrationFileExecutionRepository.save(fileExecution);
  }
}
