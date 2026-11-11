package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates Transfer transitions and immutable upload data without changing the entity. */
@Component
public class MigrationFileExecutionTransferValidator extends BaseMigrationFileExecutionValidator {

  private static final Map<MigrationFileExecutionStatus, Set<MigrationFileExecutionStatus>>
    TRANSITIONS = Map.of(
    MigrationFileExecutionStatus.EXPORTED, Set.of(MigrationFileExecutionStatus.TRANSFERING),
    MigrationFileExecutionStatus.TRANSFERING, Set.of(
      MigrationFileExecutionStatus.WAITING_PU, MigrationFileExecutionStatus.FAILED)
  );

  public MigrationFileExecutionTransferValidator() {
    super(TRANSITIONS);
  }

  public void validateMarkTransferring(MigrationFileExecution fileExecution, LocalDateTime startedAt) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.TRANSFERING);
    requireText(fileExecution.getExtractionId(), MigrationFileExecution.Fields.extractionId);
    requireText(fileExecution.getFilePath(), MigrationFileExecution.Fields.filePath);
    if (startedAt == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "uploadStartedAt must not be null");
    }
    if (fileExecution.getUploadId() != null) {
      throw new ConflictException(
        "IMMUTABLE_FIELD",
        "Field %s cannot be replaced for file execution %s".formatted(
          MigrationFileExecution.Fields.uploadId, fileExecution.getId()
        )
      );
    }
  }

  public void validateAcceptedUploadId(Long uploadId) {
    if (uploadId == null || uploadId <= 0) {
      throw new InvalidValueException("INVALID_UPLOAD_RESPONSE", "uploadId must be a positive number");
    }
  }

  public void validateRecordUploadAccepted(MigrationFileExecution fileExecution,
                                           Long uploadId,
                                           LocalDateTime completedAt) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.WAITING_PU);
    validateAcceptedUploadId(uploadId);
    if (completedAt == null) {
      throw new InvalidValueException("INVALID_UPLOAD_RESPONSE", "uploadCompletedAt must not be null");
    }
    if (fileExecution.getUploadId() != null) {
      checkImmutableField(fileExecution, MigrationFileExecution.Fields.uploadId, fileExecution.getUploadId(), uploadId);
    }
  }

  public void validateMarkTransferFailed(MigrationFileExecution fileExecution,
                                         String errorCode,
                                         String errorMessage) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.FAILED);
    requireText(errorCode, MigrationFileExecution.Fields.uploadErrorCode);
    requireText(errorMessage, MigrationFileExecution.Fields.uploadErrorMsg);
  }
}
