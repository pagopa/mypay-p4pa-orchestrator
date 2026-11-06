package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseMigrationFileExecutionValidatorTest {

  @Test
  void givenDifferentPhaseMapsWhenRequireTransitionThenUsesEachSubclassRules() {
    TestValidator exporting = new TestValidator(Map.of(
      MigrationFileExecutionStatus.PENDING, Set.of(MigrationFileExecutionStatus.EXPORTING)
    ));
    TestValidator empty = new TestValidator(Map.of());
    MigrationFileExecution execution = MigrationFileExecution.builder()
      .status(MigrationFileExecutionStatus.PENDING)
      .build();

    assertDoesNotThrow(() -> exporting.requireTransition(execution, MigrationFileExecutionStatus.EXPORTING));
    ConflictException exception = assertThrows(
      ConflictException.class, () -> empty.requireTransition(execution, MigrationFileExecutionStatus.EXPORTING)
    );

    assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
    assertEquals(MigrationFileExecutionStatus.PENDING, execution.getStatus());
  }

  @Test
  void givenNamedFieldWhenRequireTextThenUsesFieldNameInError() {
    TestValidator validator = new TestValidator(Map.of());

    InvalidValueException exception = assertThrows(
      InvalidValueException.class, () -> validator.requireText(" ", "jobId")
    );

    assertEquals("INVALID_MIGRATION_REQUEST", exception.getCode());
    assertEquals("jobId must not be blank", exception.getMessage());
    assertDoesNotThrow(() -> validator.requireText("job-123", "jobId"));
  }

  private static class TestValidator extends BaseMigrationFileExecutionValidator {
    TestValidator(Map<MigrationFileExecutionStatus, Set<MigrationFileExecutionStatus>> transitions) {
      super(transitions);
    }
  }
}
