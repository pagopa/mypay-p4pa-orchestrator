package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates Transfer transitions and immutable upload data without changing the entity.
 */
@Component
public class MigrationFileExecutionTransferValidator extends BaseMigrationFileExecutionValidator {

  private static final Map<MigrationFileExecutionStatus, Set<MigrationFileExecutionStatus>>
    TRANSITIONS = Map.of(
    MigrationFileExecutionStatus.EXPORTED, Set.of(
      MigrationFileExecutionStatus.TRANSFERING, MigrationFileExecutionStatus.FAILED),
    MigrationFileExecutionStatus.TRANSFERING, Set.of(
      MigrationFileExecutionStatus.WAITING_PU, MigrationFileExecutionStatus.FAILED),
    MigrationFileExecutionStatus.FAILED, Set.of(MigrationFileExecutionStatus.FAILED)
  );

  public MigrationFileExecutionTransferValidator() {
    super(TRANSITIONS);
  }

  public void validateMarkTransferring(MigrationFileExecution fileExecution) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.TRANSFERING);
    requireText(fileExecution.getExtractionId(), MigrationFileExecution.Fields.extractionId);
    requireText(fileExecution.getFilePath(), MigrationFileExecution.Fields.filePath);
    if (fileExecution.getUploadId() != null || fileExecution.getUploadStatus() != null) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Cannot start transfer for file execution %s with existing upload data".formatted(fileExecution.getId())
      );
    }
  }

  public void validateRecordUploadAccepted(
    MigrationFileExecution fileExecution,
    Long uploadId,
    UploadsStatusEnum uploadStatus
  ) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.WAITING_PU);
    if (uploadId == null || uploadId <= 0) {
      throw new InvalidValueException("INVALID_UPLOAD_ID", "uploadId must be a positive number");
    }
    if (uploadStatus != UploadsStatusEnum.UPLOADED) {
      throw new InvalidValueException("INVALID_UPLOAD_STATUS", "uploadStatus must be UPLOADED");
    }
    if (fileExecution.getUploadId() != null) {
      checkImmutableField(fileExecution, MigrationFileExecution.Fields.uploadId, fileExecution.getUploadId(), uploadId);
    }
    if (fileExecution.getUploadStatus() != null) {
      checkImmutableField(
        fileExecution, MigrationFileExecution.Fields.uploadStatus, fileExecution.getUploadStatus(), uploadStatus
      );
    }
  }

  public void validateMarkTransferFailed(
    MigrationFileExecution fileExecution,
    String errorCode,
    String errorMessage
  ) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.FAILED);
    requireText(errorCode, MigrationFileExecution.Fields.uploadErrorCode);
    if (fileExecution.getStatus() == MigrationFileExecutionStatus.FAILED) {
      checkImmutableField(
        fileExecution,
        MigrationFileExecution.Fields.uploadErrorCode,
        fileExecution.getUploadErrorCode(),
        errorCode
      );
      checkImmutableField(
        fileExecution,
        MigrationFileExecution.Fields.uploadErrorMsg,
        fileExecution.getUploadErrorMsg(),
        errorMessage
      );
    }
  }
}
