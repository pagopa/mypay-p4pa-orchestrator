package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates local file execution lifecycle rules without changing the entity.
 */
//TODO: Currently covers Export; Transfer and Evidence should add phase-specific validation.
@Component
public class MigrationFileExecutionValidator {

  private static final Map<MigrationFileExecutionStatus, Map<Operation, MigrationFileExecutionStatus>>
    TRANSITIONS = Map.of(
    MigrationFileExecutionStatus.PENDING, Map.of(
      Operation.START_EXPORT, MigrationFileExecutionStatus.EXPORTING,
      Operation.MARK_EXTRACTION_FAILED, MigrationFileExecutionStatus.FAILED
    ),
    MigrationFileExecutionStatus.EXPORTING, Map.of(
      Operation.RECORD_EXTRACTION_ACCEPTED, MigrationFileExecutionStatus.EXPORTING,
      Operation.MARK_EXPORTED, MigrationFileExecutionStatus.EXPORTED,
      Operation.MARK_EXTRACTION_FAILED, MigrationFileExecutionStatus.FAILED
    ),
    MigrationFileExecutionStatus.EXPORTED, Map.of(
      Operation.MARK_EXPORTED, MigrationFileExecutionStatus.EXPORTED
    ),
    MigrationFileExecutionStatus.FAILED, Map.of(
      Operation.MARK_EXTRACTION_FAILED, MigrationFileExecutionStatus.FAILED
    )
  );

  /**
   * Requires a pending execution without an accepted Extractor job.
   */
  public MigrationFileExecutionStatus validateMarkExporting(MigrationFileExecution fileExecution) {
    MigrationFileExecutionStatus targetStatus = requireTransition(fileExecution, Operation.START_EXPORT);
    // extractionId identifies the accepted Extractor job, not the file execution row.
    checkImmutableField(fileExecution, "extractionId", null, fileExecution.getExtractionId());
    return targetStatus;
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
  public MigrationFileExecutionStatus validateRecordExtractionAccepted(MigrationFileExecution fileExecution, String extractionId) {
    MigrationFileExecutionStatus targetStatus = requireTransition(fileExecution, Operation.RECORD_EXTRACTION_ACCEPTED);
    if (fileExecution.getExtractionId() != null) {
      checkImmutableField(fileExecution, "extractionId", fileExecution.getExtractionId(), extractionId);
    }
    return targetStatus;
  }

  /**
   * Requires an accepted extraction before completion; repeated completion must have identical values.
   */
  public MigrationFileExecutionStatus validateMarkExported(MigrationFileExecution fileExecution,
                                                           String filePath,
                                                           LocalDateTime completedAt) {
    MigrationFileExecutionStatus targetStatus = requireTransition(fileExecution, Operation.MARK_EXPORTED);
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
    return targetStatus;
  }

  /**
   * Allows extraction failure from pending or exporting, or a failed retry with identical error details.
   * Does not validate Transfer or PU failures.
   */
  public MigrationFileExecutionStatus validateMarkExtractionFailed(MigrationFileExecution fileExecution,
                                                         String errorCode,
                                                         String errorMessage) {
    MigrationFileExecutionStatus targetStatus = requireTransition(fileExecution, Operation.MARK_EXTRACTION_FAILED);
    if (fileExecution.getStatus() == MigrationFileExecutionStatus.FAILED) {
      checkImmutableField(fileExecution, "extractionErrorCode", fileExecution.getExtractionErrorCode(), errorCode);
      checkImmutableField(fileExecution, "extractionErrorMsg", fileExecution.getExtractionErrorMsg(), errorMessage);
    }
    return targetStatus;
  }

  private MigrationFileExecutionStatus requireTransition(MigrationFileExecution fileExecution, Operation operation) {
    MigrationFileExecutionStatus targetStatus = TRANSITIONS
      .getOrDefault(fileExecution.getStatus(), Map.of())
      .get(operation);
    if (targetStatus == null) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Cannot apply %s to file execution %s in status %s".formatted(
          operation, fileExecution.getId(), fileExecution.getStatus()
        )
      );
    }
    return targetStatus;
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

  private enum Operation {
    START_EXPORT,
    RECORD_EXTRACTION_ACCEPTED,
    MARK_EXPORTED,
    MARK_EXTRACTION_FAILED
  }
}
