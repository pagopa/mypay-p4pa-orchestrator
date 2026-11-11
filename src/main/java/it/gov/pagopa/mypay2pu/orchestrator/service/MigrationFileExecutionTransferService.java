package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferValidator;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists validated Transfer updates without modifying extraction data.
 */
@Service
public class MigrationFileExecutionTransferService extends MigrationFileExecutionService {

  private final MigrationFileExecutionRepository migrationFileExecutionRepository;
  private final MigrationFileExecutionTransferValidator validator;

  public MigrationFileExecutionTransferService(
    MigrationFileExecutionRepository migrationFileExecutionRepository,
    MigrationFileExecutionTransferValidator validator
  ) {
    super(migrationFileExecutionRepository);
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
    this.validator = validator;
  }

  public MigrationFileExecution getRequired(UUID migrationId, MigrationFileType fileType) {
    return findRequiredTransferExecution(migrationId, fileType);
  }

  /**
   * Atomically claims an exported file so concurrent requests cannot upload it twice.
   */
  @Transactional
  public MigrationFileExecution markTransferring(
    UUID migrationId,
    MigrationFileType fileType,
    LocalDateTime startedAt
  ) {
    MigrationFileExecution fileExecution = findRequiredTransferExecution(migrationId, fileType);
    validator.validateMarkTransferring(fileExecution);
    int claimed = migrationFileExecutionRepository.claimTransfer(
      migrationId,
      fileType,
      MigrationFileExecutionStatus.EXPORTED,
      MigrationFileExecutionStatus.TRANSFERING,
      startedAt
    );
    if (claimed != 1) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Transfer for migration %s and file type %s was claimed by another request"
          .formatted(migrationId, fileType)
      );
    }
    return findRequiredTransferExecution(migrationId, fileType);
  }

  public MigrationFileExecution recordUploadAccepted(
    UUID migrationId,
    MigrationFileType fileType,
    Long uploadId,
    UploadsStatusEnum uploadStatus,
    LocalDateTime completedAt
  ) {
    return update(migrationId, fileType, fileExecution -> {
      validator.validateRecordUploadAccepted(fileExecution, uploadId, uploadStatus);
      fileExecution.setStatus(MigrationFileExecutionStatus.WAITING_PU);
      fileExecution.setUploadId(uploadId);
      fileExecution.setUploadStatus(uploadStatus);
      fileExecution.setUploadCompletedAt(completedAt);
    });
  }

  public MigrationFileExecution markTransferFailed(
    UUID migrationId,
    MigrationFileType fileType,
    String errorCode,
    String errorMessage
  ) {
    return update(migrationId, fileType, fileExecution -> {
      validator.validateMarkTransferFailed(fileExecution, errorCode, errorMessage);
      fileExecution.setStatus(MigrationFileExecutionStatus.FAILED);
      fileExecution.setUploadErrorCode(errorCode);
      fileExecution.setUploadErrorMsg(errorMessage);
    });
  }

  private MigrationFileExecution findRequiredTransferExecution(
    UUID migrationId,
    MigrationFileType fileType
  ) {
    return migrationFileExecutionRepository.findByMigrationIdAndFileType(migrationId, fileType)
      .orElseThrow(() -> new NotFoundException(
        "MIGRATION_FILE_EXECUTION_NOT_FOUND",
        "File execution does not exist for migration %s and file type %s".formatted(migrationId, fileType)
      ));
  }
}
