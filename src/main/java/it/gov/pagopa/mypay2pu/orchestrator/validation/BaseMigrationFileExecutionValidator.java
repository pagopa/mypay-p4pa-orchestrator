package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * Shares lifecycle checks while keeping allowed transitions specific to each phase.
 */
public abstract class BaseMigrationFileExecutionValidator {

  private final Map<MigrationFileExecutionStatus, Set<MigrationFileExecutionStatus>> transitions;

  protected BaseMigrationFileExecutionValidator(Map<MigrationFileExecutionStatus,
                                                Set<MigrationFileExecutionStatus>> transitions) {
    this.transitions = Map.copyOf(transitions);
  }

  protected final void requireText(String value, String fieldName) {
    if (!StringUtils.hasText(value)) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", fieldName + " must not be blank");
    }
  }

  protected final void requireStatus(MigrationFileExecution fileExecution, MigrationFileExecutionStatus expectedStatus) {
    if (fileExecution.getStatus() != expectedStatus) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "File execution %s must be in status %s, but is %s".formatted(
          fileExecution.getId(), expectedStatus, fileExecution.getStatus()
        )
      );
    }
  }

  protected final void requireTransition(MigrationFileExecution fileExecution, MigrationFileExecutionStatus targetStatus) {
    if (!transitions.getOrDefault(fileExecution.getStatus(), Set.of()).contains(targetStatus)) {
      throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Cannot transition file execution %s from %s to %s".formatted(
          fileExecution.getId(), fileExecution.getStatus(), targetStatus
        )
      );
    }
  }

  protected final void checkImmutableField(MigrationFileExecution fileExecution,
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
