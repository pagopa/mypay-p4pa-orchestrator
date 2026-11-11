package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferValidator;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Persists validated Transfer updates without modifying extraction data. */
@Service
public class MigrationFileExecutionTransferService extends MigrationFileExecutionService {

  private final MigrationFileExecutionTransferValidator validator;
  private final MigrationFileExecutionRepository migrationFileExecutionRepository;

  public MigrationFileExecutionTransferService(
    MigrationFileExecutionRepository migrationFileExecutionRepository,
    MigrationFileExecutionTransferValidator validator
  ) {
    super(migrationFileExecutionRepository);
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
    this.validator = validator;
  }

  public MigrationFileExecution getRequired(UUID migrationId, MigrationFileType fileType) {
    return findRequired(migrationId, fileType);
  }

  public MigrationFileExecution markTransferring(
    UUID migrationId,
    MigrationFileType fileType,
    LocalDateTime startedAt
  ) {
    MigrationFileExecution fileExecution = findRequired(migrationId, fileType);
    validator.validateMarkTransferring(fileExecution, startedAt);
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
        "File execution for migration %s and file type %s is no longer available for transfer"
          .formatted(migrationId, fileType)
      );
    }
    return findRequired(migrationId, fileType);
  }

  public MigrationFileExecution recordUploadAccepted(
    UUID migrationId,
    MigrationFileType fileType,
    Long uploadId,
    LocalDateTime completedAt
  ) {
    validator.validateAcceptedUploadId(uploadId);
    return update(migrationId, fileType, fileExecution -> {
      validator.validateRecordUploadAccepted(fileExecution, uploadId, completedAt);
      fileExecution.setStatus(MigrationFileExecutionStatus.WAITING_PU);
      fileExecution.setUploadId(uploadId);
      fileExecution.setUploadStatus(UploadsStatusEnum.UPLOADED);
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
}
