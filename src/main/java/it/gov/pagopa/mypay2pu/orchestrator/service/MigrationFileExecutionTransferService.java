package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationFileExecutionDetailMapper;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferValidator;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
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
  private final MigrationFileExecutionDetailMapper migrationFileExecutionDetailMapper;
  private final MigrationFileExecutionTransferValidator validator;

  public MigrationFileExecutionTransferService(
    MigrationFileExecutionRepository migrationFileExecutionRepository,
    MigrationFileExecutionDetailRepository migrationFileExecutionDetailRepository,
    MigrationFileExecutionDetailMapper migrationFileExecutionDetailMapper,
    MigrationFileExecutionTransferValidator validator
  ) {
    super(migrationFileExecutionRepository);
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
    this.migrationFileExecutionDetailRepository = migrationFileExecutionDetailRepository;
    this.migrationFileExecutionDetailMapper = migrationFileExecutionDetailMapper;
    this.validator = validator;
  }

  public MigrationFileExecution getRequired(UUID migrationId, MigrationFileType fileType) {
    return findRequiredMigrationFileExecution(migrationId, fileType);
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
    MigrationFileExecution fileExecution = findRequiredMigrationFileExecution(migrationId, fileType);
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
    return fileExecution;
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

  /**
   * Persists the upload metadata returned by PU for the accepted upload.
   */
  @Transactional
  public List<MigrationFileExecutionDetail> recordUploadDetails(
    MigrationFileExecution fileExecution,
    List<UploadDetails> uploadDetails
  ) {
    Long uploadId = fileExecution.getUploadId();
    validateUploadId(uploadId);
    if (uploadDetails == null) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "PU returned no upload details for migration %s and file type %s"
          .formatted(fileExecution.getMigrationId(), fileExecution.getFileType())
      );
    }

    List<MigrationFileExecutionDetail> existingDetails =
      migrationFileExecutionDetailRepository.findAllByMigrationFileExecutionId(fileExecution.getId());
    if (!existingDetails.isEmpty()) {
      return existingDetails;
    }

    List<MigrationFileExecutionDetail> details = uploadDetails.stream()
      .map(uploadDetail -> migrationFileExecutionDetailMapper.toMigrationFileExecutionDetail(
        fileExecution.getId(), uploadId, uploadDetail
      ))
      .toList();
    return migrationFileExecutionDetailRepository.saveAll(details);
  }

  @Transactional(readOnly = true)
  public boolean hasUploadDetails(MigrationFileExecution fileExecution) {
    validateUploadId(fileExecution.getUploadId());
    return migrationFileExecutionDetailRepository.existsByMigrationFileExecutionId(fileExecution.getId());
  }

  private void validateUploadId(Long uploadId) {
    if (uploadId == null || uploadId <= 0) {
      throw new InvalidValueException("INVALID_UPLOAD_ID", "uploadId must be a positive number");
    }
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

  private MigrationFileExecution findRequiredMigrationFileExecution(
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
