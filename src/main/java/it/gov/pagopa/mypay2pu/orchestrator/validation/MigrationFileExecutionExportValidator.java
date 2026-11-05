package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates Export transitions and immutable extraction data without changing the entity.
 */
@Component
public class MigrationFileExecutionExportValidator extends BaseMigrationFileExecutionValidator {

  private static final Map<MigrationFileExecutionStatus, Set<MigrationFileExecutionStatus>>
    TRANSITIONS = Map.of(
    MigrationFileExecutionStatus.PENDING, Set.of(
      MigrationFileExecutionStatus.EXPORTING, MigrationFileExecutionStatus.FAILED),
    MigrationFileExecutionStatus.EXPORTING, Set.of(
      MigrationFileExecutionStatus.EXPORTED, MigrationFileExecutionStatus.FAILED),
    MigrationFileExecutionStatus.EXPORTED, Set.of(MigrationFileExecutionStatus.EXPORTED),
    MigrationFileExecutionStatus.FAILED, Set.of(MigrationFileExecutionStatus.FAILED)
  );

  public MigrationFileExecutionExportValidator() {
    super(TRANSITIONS);
  }

  /**
   * Requires a pending execution without an accepted Extractor job.
   */
  public void validateMarkExporting(MigrationFileExecution fileExecution) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.EXPORTING);
    // extractionId identifies the accepted Extractor job, not the file execution row.
    checkImmutableField(fileExecution, MigrationFileExecution.Fields.extractionId, null, fileExecution.getExtractionId());
  }

  /**
   * Rejects null, empty or whitespace-only Extractor job IDs.
   */
  public void validateAcceptedExtractionId(String extractionId) {
    requireText(extractionId, MigrationFileExecution.Fields.extractionId);
  }

  /**
   * Records acceptance without a state transition: ACCEPTED is not a file execution status.
   * Repeated acceptance is allowed only with the same ID while exporting.
   */
  public void validateRecordExtractionAccepted(MigrationFileExecution fileExecution, String extractionId) {
    requireStatus(fileExecution, MigrationFileExecutionStatus.EXPORTING);
    if (fileExecution.getExtractionId() != null) {
      checkImmutableField(
        fileExecution, MigrationFileExecution.Fields.extractionId, fileExecution.getExtractionId(), extractionId
      );
    }
  }

  /**
   * Requires an accepted extraction before completion; only identical completion notifications
   * may be repeated. Starting another extraction on the same execution remains forbidden.
   */
  public void validateMarkExported(MigrationFileExecution fileExecution,
                                   String filePath,
                                   LocalDateTime completedAt) {
    requireTransition(fileExecution, MigrationFileExecutionStatus.EXPORTED);
    if (fileExecution.getStatus() == MigrationFileExecutionStatus.EXPORTED) {
      checkImmutableField(fileExecution, MigrationFileExecution.Fields.filePath, fileExecution.getFilePath(), filePath);
      checkImmutableField(
        fileExecution, MigrationFileExecution.Fields.extractionCompletedAt,
        fileExecution.getExtractionCompletedAt(), completedAt
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
      checkImmutableField(
        fileExecution, MigrationFileExecution.Fields.extractionErrorCode, fileExecution.getExtractionErrorCode(), errorCode
      );
      checkImmutableField(
        fileExecution, MigrationFileExecution.Fields.extractionErrorMsg, fileExecution.getExtractionErrorMsg(), errorMessage
      );
    }
  }
}
