package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferValidator;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
    UUID migrationId,
    MigrationFileType fileType,
    Long uploadId,
    List<UploadDetails> uploadDetails
  ) {
    validateUploadId(uploadId);
    MigrationFileExecution fileExecution = findRequiredMigrationFileExecutionForUpdate(migrationId, fileType);
    validateAcceptedUpload(fileExecution, migrationId, fileType, uploadId);
    if (uploadDetails == null) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "PU returned no upload details for migration %s and file type %s".formatted(migrationId, fileType)
      );
    }

    List<MigrationFileExecutionDetail> existingDetails =
      migrationFileExecutionDetailRepository.findAllByMigrationFileExecutionId(fileExecution.getId());
    if (!existingDetails.isEmpty()) {
      return existingDetails;
    }

    List<MigrationFileExecutionDetail> details = uploadDetails.stream()
      .map(uploadDetail -> toMigrationFileExecutionDetail(fileExecution.getId(), uploadId, uploadDetail))
      .toList();
    return migrationFileExecutionDetailRepository.saveAll(details);
  }

  @Transactional(readOnly = true)
  public boolean hasUploadDetails(UUID migrationId, MigrationFileType fileType, Long uploadId) {
    MigrationFileExecution fileExecution = findAcceptedUpload(migrationId, fileType, uploadId);
    return migrationFileExecutionDetailRepository.existsByMigrationFileExecutionId(fileExecution.getId());
  }

  private MigrationFileExecution findAcceptedUpload(
    UUID migrationId,
    MigrationFileType fileType,
    Long uploadId
  ) {
    validateUploadId(uploadId);
    MigrationFileExecution fileExecution = findRequiredMigrationFileExecution(migrationId, fileType);
    validateAcceptedUpload(fileExecution, migrationId, fileType, uploadId);
    return fileExecution;
  }

  private void validateUploadId(Long uploadId) {
    if (uploadId == null || uploadId <= 0) {
      throw new InvalidValueException("INVALID_UPLOAD_ID", "uploadId must be a positive number");
    }
  }

  private void validateAcceptedUpload(
    MigrationFileExecution fileExecution,
    UUID migrationId,
    MigrationFileType fileType,
    Long uploadId
  ) {
    if (fileExecution.getStatus() != MigrationFileExecutionStatus.WAITING_PU
      || !Objects.equals(fileExecution.getUploadId(), uploadId)) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Upload details do not match the accepted upload for migration %s and file type %s"
          .formatted(migrationId, fileType)
      );
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

  private MigrationFileExecution findRequiredMigrationFileExecutionForUpdate(
    UUID migrationId,
    MigrationFileType fileType
  ) {
    return migrationFileExecutionRepository.findByMigrationIdAndFileTypeForUpdate(migrationId, fileType)
      .orElseThrow(() -> new NotFoundException(
        "MIGRATION_FILE_EXECUTION_NOT_FOUND",
        "File execution does not exist for migration %s and file type %s".formatted(migrationId, fileType)
      ));
  }

  private MigrationFileExecutionDetail toMigrationFileExecutionDetail(
    UUID migrationFileExecutionId,
    Long uploadId,
    UploadDetails uploadDetail
  ) {
    if (uploadDetail == null
      || !Objects.equals(uploadDetail.getUploadId(), uploadId)
      || StringUtils.isBlank(uploadDetail.getFileName())
      || uploadDetail.getFileSize() < 0) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "PU returned invalid upload details for upload %s".formatted(uploadId)
      );
    }

    Integer numTotalRows = toNonNegativeInteger(uploadDetail.getNumTotalRows(), "numTotalRows");
    Integer numCorrectlyImportedRows =
      toNonNegativeInteger(uploadDetail.getNumCorrectlyImportedRows(), "numCorrectlyImportedRows");
    if (numTotalRows != null && numCorrectlyImportedRows != null && numCorrectlyImportedRows > numTotalRows) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "numCorrectlyImportedRows exceeds numTotalRows for upload %s".formatted(uploadId)
      );
    }

    return MigrationFileExecutionDetail.builder()
      .migrationFileExecutionId(migrationFileExecutionId)
      .fileName(uploadDetail.getFileName())
      .uploadDetailId(uploadDetail.getUploadDetailId())
      .fileSize(uploadDetail.getFileSize())
      .status(toLocalStatus(uploadDetail.getStatus()))
      .errorDescription(uploadDetail.getErrorDescription())
      .discardFileName(uploadDetail.getDiscardFileName())
      .numTotalRows(numTotalRows)
      .numCorrectlyImportedRows(numCorrectlyImportedRows)
      .build();
  }

  private Integer toNonNegativeInteger(Long value, String fieldName) {
    if (value == null) {
      return null;
    }
    int intValue;
    try {
      intValue = Math.toIntExact(value);
    } catch (ArithmeticException exception) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "%s exceeds the supported range".formatted(fieldName)
      );
    }
    if (intValue < 0) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "%s must not be negative".formatted(fieldName)
      );
    }
    return intValue;
  }

  private MigrationFileExecutionDetailStatus toLocalStatus(IngestionFlowFileStatus status) {
    return switch (status) {
      case UPLOADED, PROCESSING, WAITING_FILE -> MigrationFileExecutionDetailStatus.WAITING_PU;
      case COMPLETED -> MigrationFileExecutionDetailStatus.COMPLETED;
      case WARNING -> MigrationFileExecutionDetailStatus.PARTIAL;
      case ERROR -> MigrationFileExecutionDetailStatus.FAILED;
    };
  }
}
