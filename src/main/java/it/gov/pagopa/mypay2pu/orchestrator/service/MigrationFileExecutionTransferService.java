package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferValidator;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Persists validated Transfer updates without modifying extraction data.
 */
@Service
public class MigrationFileExecutionTransferService extends MigrationFileExecutionService {

  private final MigrationFileExecutionRepository migrationFileExecutionRepository;
  private final MigrationFileExecutionDetailRepository migrationFileExecutionDetailRepository;
  private final MigrationFileExecutionTransferValidator validator;

  public MigrationFileExecutionTransferService(
    MigrationFileExecutionRepository migrationFileExecutionRepository,
    MigrationFileExecutionDetailRepository migrationFileExecutionDetailRepository,
    MigrationFileExecutionTransferValidator validator
  ) {
    super(migrationFileExecutionRepository);
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
    this.migrationFileExecutionDetailRepository = migrationFileExecutionDetailRepository;
    this.validator = validator;
  }

  public MigrationFileExecution getRequired(UUID migrationId, MigrationFileType fileType) {
    return findRequired(migrationId, fileType);
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
    MigrationFileExecution fileExecution = findRequired(migrationId, fileType);
    validator.validateMarkTransferring(fileExecution);
    int claimed = migrationFileExecutionRepository.claimTransfer(
      migrationId,
      fileType,
      startedAt
    );
    if (claimed != 1) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Transfer for migration %s and file type %s was claimed by another request"
          .formatted(migrationId, fileType)
      );
    }
    fileExecution.setStatus(MigrationFileExecutionStatus.TRANSFERING);
    fileExecution.setUploadStartedAt(startedAt);
    updateDetailStatuses(
      fileExecution.getId(), MigrationFileExecutionDetailStatus.TRANSFERING
    );
    return fileExecution;
  }

  @Transactional
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
      updateDetailStatuses(
        fileExecution.getId(), MigrationFileExecutionDetailStatus.WAITING_PU
      );
    });
  }

  @Transactional
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
      updateDetailStatuses(
        fileExecution.getId(), MigrationFileExecutionDetailStatus.FAILED
      );
    });
  }

  private void updateDetailStatuses(UUID fileExecutionId, MigrationFileExecutionDetailStatus status) {
    List<MigrationFileExecutionDetail> details =
      migrationFileExecutionDetailRepository.findAllByMigrationFileExecutionId(fileExecutionId);
    details.forEach(detail -> detail.setStatus(status));
    migrationFileExecutionDetailRepository.saveAll(details);
  }
}
