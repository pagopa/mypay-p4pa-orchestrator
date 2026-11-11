package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationFileExecutionTransferValidatorTest {

  private static final UUID EXECUTION_ID = UUID.fromString("51862f2c-dc7e-4358-beb5-f0e3289781d3");
  private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 10, 55);
  private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 10, 56);
  private final MigrationFileExecutionTransferValidator validator = new MigrationFileExecutionTransferValidator();

  @Test
  void givenExportedExecutionWhenValidateMarkTransferringThenAllowsTransition() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTED);

    assertDoesNotThrow(() -> validator.validateMarkTransferring(execution, STARTED_AT));
    assertEquals(MigrationFileExecutionStatus.EXPORTED, execution.getStatus());
  }

  @Test
  void givenTransferringExecutionWhenValidateRecordUploadAcceptedThenAllowsFirstUploadId() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);

    assertDoesNotThrow(() -> validator.validateRecordUploadAccepted(execution, 42L, COMPLETED_AT));
    assertEquals(MigrationFileExecutionStatus.TRANSFERING, execution.getStatus());
  }

  @Test
  void givenTransferringExecutionWhenValidateMarkTransferFailedThenAllowsTransferFailure() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);

    assertDoesNotThrow(() -> validator.validateMarkTransferFailed(execution, "UPLOAD_ERROR", "Upload failed"));
    assertEquals(MigrationFileExecutionStatus.TRANSFERING, execution.getStatus());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0, -1})
  void givenInvalidUploadIdWhenValidateAcceptedUploadIdThenThrowsInvalidValueException(Long uploadId) {
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validateAcceptedUploadId(uploadId)
    );

    assertEquals("INVALID_UPLOAD_RESPONSE", exception.getCode());
    assertEquals("uploadId must be a positive number", exception.getMessage());
  }

  @Test
  void givenExportedExecutionWithExistingUploadIdWhenValidateMarkTransferringThenRejectsRestart() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    execution.setUploadId(41L);

    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> validator.validateMarkTransferring(execution, STARTED_AT)
    );

    assertEquals("IMMUTABLE_FIELD", exception.getCode());
  }

  @Test
  void givenTransferringExecutionWhenValidateRecordUploadAcceptedThenRejectsMissingTimestamp() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validateRecordUploadAccepted(execution, 42L, null)
    );

    assertEquals("INVALID_UPLOAD_RESPONSE", exception.getCode());
  }

  private MigrationFileExecution fileExecution(MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder()
      .id(EXECUTION_ID)
      .migrationId(UUID.randomUUID())
      .fileType(it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType.ORGANIZATIONS)
      .status(status)
      .extractionId("extractor-123")
      .filePath("organizations.zip")
      .extractionCompletedAt(STARTED_AT)
      .uploadStatus(status == MigrationFileExecutionStatus.WAITING_PU ? UploadsStatusEnum.UPLOADED : null)
      .build();
  }
}
