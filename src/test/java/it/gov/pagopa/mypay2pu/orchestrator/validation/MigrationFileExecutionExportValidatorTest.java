package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationFileExecutionExportValidatorTest {

  private static final UUID EXECUTION_ID = UUID.randomUUID();
  private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, Month.OCTOBER, 27, 16, 30);
  private static final String FILE_PATH = "archive/organizations.zip";
  private final MigrationFileExecutionExportValidator validator = new MigrationFileExecutionExportValidator();

  @ParameterizedTest
  @EnumSource(MigrationFileExecutionStatus.class)
  void givenFileExecutionStatusWhenValidateMarkExportingThenAllowsOnlyPending(MigrationFileExecutionStatus status) {
    MigrationFileExecution execution = fileExecution(status);
    execution.setExtractionId(null);

    if (status == MigrationFileExecutionStatus.PENDING) {
      assertDoesNotThrow(() -> validator.validateMarkExporting(execution));
    } else {
      ConflictException exception = assertThrows(
        ConflictException.class, () -> validator.validateMarkExporting(execution)
      );
      assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
      assertTrue(exception.getMessage().contains("to EXPORTING"));
      assertTrue(exception.getMessage().contains(EXECUTION_ID.toString()));
      assertTrue(exception.getMessage().contains(status.name()));
    }
    assertEquals(status, execution.getStatus());
  }

  @ParameterizedTest
  @EnumSource(MigrationFileExecutionStatus.class)
  void givenFileExecutionStatusWhenValidateRecordExtractionAcceptedThenAllowsOnlyExporting(MigrationFileExecutionStatus status) {
    MigrationFileExecution execution = fileExecution(status);

    if (status == MigrationFileExecutionStatus.EXPORTING) {
      assertDoesNotThrow(() -> validator.validateRecordExtractionAccepted(execution, "extractor-123"));
    } else {
      ConflictException exception = assertThrows(
        ConflictException.class, () -> validator.validateRecordExtractionAccepted(execution, "extractor-123")
      );
      assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
    }
    assertEquals(status, execution.getStatus());
  }

  @ParameterizedTest
  @EnumSource(MigrationFileExecutionStatus.class)
  void givenFileExecutionStatusWhenValidateMarkExportedThenAllowsOnlyExportingOrExported(
    MigrationFileExecutionStatus status
  ) {
    MigrationFileExecution execution = fileExecution(status);

    if (status == MigrationFileExecutionStatus.EXPORTING || status == MigrationFileExecutionStatus.EXPORTED) {
      assertDoesNotThrow(() -> validator.validateMarkExported(execution, FILE_PATH, COMPLETED_AT));
    } else {
      ConflictException exception = assertThrows(
        ConflictException.class, () -> validator.validateMarkExported(execution, FILE_PATH, COMPLETED_AT)
      );
      assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
    }
    assertEquals(status, execution.getStatus());
  }

  @ParameterizedTest
  @EnumSource(MigrationFileExecutionStatus.class)
  void givenFileExecutionStatusWhenValidateMarkExtractionFailedThenAllowsOnlyPendingExportingOrFailed(
    MigrationFileExecutionStatus status
  ) {
    MigrationFileExecution execution = fileExecution(status);

    if (status == MigrationFileExecutionStatus.PENDING
      || status == MigrationFileExecutionStatus.EXPORTING
      || status == MigrationFileExecutionStatus.FAILED) {
      assertDoesNotThrow(() -> validator.validateMarkExtractionFailed(execution, "ERROR", "Extraction failed"));
    } else {
      ConflictException exception = assertThrows(
        ConflictException.class, () -> validator.validateMarkExtractionFailed(execution, "ERROR", "Extraction failed")
      );
      assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
    }
    assertEquals(status, execution.getStatus());
  }

  @Test
  void givenPendingExecutionWithAcceptedExtractorJobWhenValidateMarkExportingThenRejectsExistingJob() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.PENDING);

    ConflictException exception = assertThrows(
      ConflictException.class, () -> validator.validateMarkExporting(execution)
    );

    assertEquals("IMMUTABLE_FIELD", exception.getCode());
    assertTrue(exception.getMessage().contains("extractionId"));
    assertEquals("extractor-123", execution.getExtractionId());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void givenInvalidExtractionIdWhenValidateAcceptedExtractionIdThenThrowsInvalidValueException(String extractionId) {
    InvalidValueException exception = assertThrows(
      InvalidValueException.class, () -> validator.validateAcceptedExtractionId(extractionId)
    );

    assertEquals("INVALID_MIGRATION_REQUEST", exception.getCode());
    assertEquals("extractionId must not be blank", exception.getMessage());
  }

  @Test
  void givenValidExtractionIdWhenValidateAcceptedExtractionIdThenAcceptsInput() {
    assertDoesNotThrow(() -> validator.validateAcceptedExtractionId("extractor-123"));
  }

  @Test
  void givenNoAcceptedJobWhenValidateRecordExtractionAcceptedThenAllowsFirstId() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTING);
    execution.setExtractionId(null);

    assertDoesNotThrow(() -> validator.validateRecordExtractionAccepted(execution, "extractor-123"));
    assertNull(execution.getExtractionId());
  }

  @Test
  void givenAcceptedJobWhenValidateRecordExtractionAcceptedThenRejectsDifferentId() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTING);

    ConflictException exception = assertThrows(
      ConflictException.class, () -> validator.validateRecordExtractionAccepted(execution, "extractor-456")
    );

    assertEquals("IMMUTABLE_FIELD", exception.getCode());
    assertTrue(exception.getMessage().contains("extractionId"));
    assertEquals("extractor-123", execution.getExtractionId());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void givenMissingAcceptedJobWhenValidateMarkExportedThenRejectsCompletion(String extractionId) {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTING);
    execution.setExtractionId(extractionId);

    ConflictException exception = assertThrows(
      ConflictException.class, () -> validator.validateMarkExported(execution, FILE_PATH, COMPLETED_AT)
    );

    assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
    assertEquals(
      "Cannot complete file execution %s before an accepted Extractor job ID is persisted".formatted(EXECUTION_ID),
      exception.getMessage()
    );
    assertEquals(extractionId, execution.getExtractionId());
    assertEquals(MigrationFileExecutionStatus.EXPORTING, execution.getStatus());
  }

  @ParameterizedTest
  @CsvSource({
    "archive/other.zip, false, filePath",
    "archive/organizations.zip, true, extractionCompletedAt"
  })
  void givenChangedExportResultWhenValidateMarkExportedThenRejectsImmutableField(
    String filePath, boolean differentTime, String fieldName
  ) {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    LocalDateTime completedAt = differentTime ? COMPLETED_AT.plusMinutes(1) : COMPLETED_AT;

    ConflictException exception = assertThrows(
      ConflictException.class, () -> validator.validateMarkExported(execution, filePath, completedAt)
    );

    assertEquals("IMMUTABLE_FIELD", exception.getCode());
    assertTrue(exception.getMessage().contains(fieldName));
    assertEquals(FILE_PATH, execution.getFilePath());
    assertEquals(COMPLETED_AT, execution.getExtractionCompletedAt());
  }

  @ParameterizedTest
  @CsvSource({
    "OTHER_ERROR, Extraction failed, extractionErrorCode",
    "ERROR, Other message, extractionErrorMsg",
    ", Extraction failed, extractionErrorCode",
    "ERROR, , extractionErrorMsg"
  })
  void givenChangedFailureDetailsWhenValidateMarkExtractionFailedThenRejectsImmutableField(
    String errorCode, String errorMessage, String fieldName
  ) {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.FAILED);

    ConflictException exception = assertThrows(
      ConflictException.class, () -> validator.validateMarkExtractionFailed(execution, errorCode, errorMessage)
    );

    assertEquals("IMMUTABLE_FIELD", exception.getCode());
    assertTrue(exception.getMessage().contains(fieldName));
    assertEquals("ERROR", execution.getExtractionErrorCode());
    assertEquals("Extraction failed", execution.getExtractionErrorMsg());
  }

  @Test
  void givenFailedExecutionWithNullDetailsWhenValidateMarkExtractionFailedThenAcceptsIdenticalRetry() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.FAILED);
    execution.setExtractionErrorCode(null);
    execution.setExtractionErrorMsg(null);

    assertDoesNotThrow(() -> validator.validateMarkExtractionFailed(execution, null, null));
  }

  private MigrationFileExecution fileExecution(MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder()
      .id(EXECUTION_ID)
      .status(status)
      .extractionId("extractor-123")
      .filePath(FILE_PATH)
      .extractionCompletedAt(COMPLETED_AT)
      .extractionErrorCode("ERROR")
      .extractionErrorMsg("Extraction failed")
      .build();
  }
}
