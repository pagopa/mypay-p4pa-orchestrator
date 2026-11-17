package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.BaseBusinessException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationToolkitSharedFolderService;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Executes the shared upload lifecycle for a single migration file type.
 *
 * <p>Subclasses configure their file type and inherit archive validation, PU submission, and
 * persistence behavior. PU processing results remain owned by the separate Evidence phase.
 */
public abstract class AbstractMigrationTransferOperation implements MigrationTransferOperation {

  private static final String DEFAULT_TRANSFER_ERROR = "TRANSFER_FAILED";

  private final MigrationFileType fileType;
  private final MigrationFileExecutionTransferService transferService;
  private final MigrationToolkitSharedFolderService archiveService;
  private final PuMigrationService puMigrationService;

  protected AbstractMigrationTransferOperation(
    MigrationFileType fileType,
    MigrationFileExecutionTransferService transferService,
    MigrationToolkitSharedFolderService archiveService,
    PuMigrationService puMigrationService
  ) {
    this.fileType = Objects.requireNonNull(fileType, "fileType");
    this.transferService = Objects.requireNonNull(transferService, "transferService");
    this.archiveService = Objects.requireNonNull(archiveService, "archiveService");
    this.puMigrationService = Objects.requireNonNull(puMigrationService, "puMigrationService");
  }

  @Override
  public final MigrationFileType getMigrationFileType() {
    return fileType;
  }

  /**
   * Uploads the exported archive once and persists the accepted upload as WAITING_PU.
   *
   * @param request migration request containing its ID and requested IPA codes
   */
  @Override
  public final void execute(MigrationRequestDto request) {
    UUID migrationId = request.getMigrationId();
    MigrationFileExecution fileExecution = transferService.getRequired(migrationId, fileType);

    MigrationFileExecutionStatus status = fileExecution.getStatus();
    if (status == MigrationFileExecutionStatus.WAITING_PU
      || status == MigrationFileExecutionStatus.COMPLETED || status == MigrationFileExecutionStatus.WARNING) {
      return;
    }

    switch (status) {
      case TRANSFERING -> throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Transfer for migration %s and file type %s is already in progress"
          .formatted(migrationId, fileType)
      );
      case FAILED -> throw alreadyFailed(fileExecution);
      case EXPORTED -> uploadExportedFile(request, fileExecution);
      default -> throw new ConflictException(
        "TRANSFER_FILE_NOT_READY",
        "File type %s for migration %s is not ready for transfer (status=%s)"
          .formatted(fileType, migrationId, fileExecution.getStatus())
      );
    }
  }

  private void uploadExportedFile(MigrationRequestDto request, MigrationFileExecution fileExecution) {
    UUID migrationId = request.getMigrationId();
    Resource archive;
    try {
      archive = archiveService.getDataArchive(fileExecution, fileType);
    } catch (RuntimeException failure) {
      markTransferFailed(migrationId, failure);
      throw failure;
    }

    transferService.markTransferring(migrationId, fileType, LocalDateTime.now(ZoneOffset.UTC));
    try {
      UploadMigrationFileResponseDTO response = puMigrationService.uploadMigrationFile(
        fileType,
        archive
      );
      Long uploadId = response == null ? null : response.getUploadId();
      if (uploadId == null || uploadId <= 0) {
        throw new InvalidValueException(
          "TRANSFER_INVALID_UPLOAD_RESPONSE",
          "PU returned no valid upload ID for migration %s and file type %s".formatted(migrationId, fileType)
        );
      }
      transferService.recordUploadAccepted(
        migrationId,
        fileType,
        uploadId,
        UploadsStatusEnum.UPLOADED,
        LocalDateTime.now(ZoneOffset.UTC)
      );
    } catch (RuntimeException failure) {
      markTransferFailed(migrationId, failure);
      throw failure;
    }
  }

  private void markTransferFailed(UUID migrationId, RuntimeException failure) {
    String errorCode = transferErrorCode(failure);
    String errorMessage = StringUtils.isBlank(failure.getMessage())
      ? failure.getClass().getSimpleName()
      : failure.getMessage();
    try {
      transferService.markTransferFailed(migrationId, fileType, errorCode, errorMessage);
    } catch (RuntimeException persistenceFailure) {
      failure.addSuppressed(persistenceFailure);
    }
  }

  private String transferErrorCode(RuntimeException failure) {
    if (failure instanceof BaseBusinessException businessFailure) {
      return businessFailure.getCode();
    }
    return DEFAULT_TRANSFER_ERROR;
  }

  private ConflictException alreadyFailed(MigrationFileExecution fileExecution) {
    String errorCode = fileExecution.getUploadErrorCode();
    if (StringUtils.isBlank(errorCode)) {
      errorCode = fileExecution.getExtractionErrorCode();
    }
    if (StringUtils.isBlank(errorCode)) {
      errorCode = "TRANSFER_ALREADY_FAILED";
    }
    String errorMessage = fileExecution.getUploadErrorMsg();
    if (StringUtils.isBlank(errorMessage)) {
      errorMessage = fileExecution.getExtractionErrorMsg();
    }
    if (StringUtils.isBlank(errorMessage)) {
      errorMessage = "Transfer is already FAILED";
    }
    return new ConflictException(errorCode, errorMessage);
  }
}
