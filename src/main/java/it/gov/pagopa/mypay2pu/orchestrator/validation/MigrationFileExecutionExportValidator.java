package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates Export transitions and immutable extraction data without changing the entity.
 */
@Component
public class MigrationFileExecutionExportValidator {

  private static final Map<MigrationFileExecutionStatus, Set<MigrationFileExecutionStatus>>
    TRANSITIONS = Map.of(
    MigrationFileExecutionStatus.PENDING, Set.of(
      MigrationFileExecutionStatus.EXPORTING, MigrationFileExecutionStatus.FAILED),
    MigrationFileExecutionStatus.EXPORTING, Set.of(
      MigrationFileExecutionStatus.EXPORTED, MigrationFileExecutionStatus.FAILED),
    MigrationFileExecutionStatus.EXPORTED, Set.of(MigrationFileExecutionStatus.EXPORTED),
    MigrationFileExecutionStatus.FAILED, Set.of(MigrationFileExecutionStatus.FAILED)
  );

  /**
   * Requires a pending execution without an accepted Extractor job.
   */
  public void validateMarkExporting(MigrationFileExecution fileExecution) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.EXPORTING);
    // extractionId identifies the accepted Extractor job, not the file execution row.
    checkImmutableField(fileExecution, "extractionId", null, fileExecution.getExtractionId());
  }

  /**
   * Rejects null, empty or whitespace-only Extractor job IDs.
   */
  public void validateAcceptedExtractionId(String extractionId) {
    if (!StringUtils.hasText(extractionId)) {
      throw new InvalidValueException(
        "INVALID_MIGRATION_REQUEST", "extractionId must not be blank"
      );
    }
  }

  /**
   * Allows recording an Extractor job ID while exporting, but not replacing an existing ID.
   */
  public void validateRecordExtractionAccepted(MigrationFileExecution fileExecution, String extractionId) {
    if (fileExecution.getStatus() != MigrationFileExecutionStatus.EXPORTING) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Cannot record an accepted Extractor job ID for file execution %s in status %s".formatted(
          fileExecution.getId(), fileExecution.getStatus()
        )
      );
    }
    if (fileExecution.getExtractionId() != null) {
      checkImmutableField(fileExecution, "extractionId", fileExecution.getExtractionId(), extractionId);
    }
  }

  /**
   * Requires an accepted extraction before completion; repeated completion must have identical values.
   */
  public void validateMarkExported(MigrationFileExecution fileExecution,
                                   String filePath,
                                   LocalDateTime completedAt) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.EXPORTED);
    if (fileExecution.getStatus() == MigrationFileExecutionStatus.EXPORTED) {
      checkImmutableField(fileExecution, "filePath", fileExecution.getFilePath(), filePath);
      checkImmutableField(
        fileExecution, "extractionCompletedAt", fileExecution.getExtractionCompletedAt(), completedAt
      );
    } else if (!StringUtils.hasText(fileExecution.getExtractionId())) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Cannot complete file execution %s before an accepted Extractor job ID is persisted"
          .formatted(fileExecution.getId())
      );
    }
  }

  /**
   * Allows extraction failure from pending or exporting, or a failed retry with identical error details.
   * Does not validate Transfer or PU failures.
   */
  public void validateMarkExtractionFailed(MigrationFileExecution fileExecution,
                                           String errorCode,
                                           String errorMessage) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.FAILED);
    if (fileExecution.getStatus() == MigrationFileExecutionStatus.FAILED) {
      checkImmutableField(fileExecution, "extractionErrorCode", fileExecution.getExtractionErrorCode(), errorCode);
      checkImmutableField(fileExecution, "extractionErrorMsg", fileExecution.getExtractionErrorMsg(), errorMessage);
    }
  }

  private void requireTransition(MigrationFileExecution fileExecution, MigrationFileExecutionStatus targetStatus) {
    if (!TRANSITIONS.getOrDefault(fileExecution.getStatus(), Set.of()).contains(targetStatus)) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Cannot transition file execution %s from %s to %s".formatted(
          fileExecution.getId(), fileExecution.getStatus(), targetStatus
        )
      );
    }
  }

  private void checkImmutableField(MigrationFileExecution fileExecution,
                                   String fieldName,
                                   Object original,
                                   Object updated) {
    if (!Objects.equals(original, updated)) {
      throw new ConflictException(
        "IMMUTABLE_FIELD",
        "Field %s cannot be updated for file execution %s in status %s".formatted(
          fieldName, fileExecution.getId(), fileExecution.getStatus()
        )
      );
    }
  }

}
