package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationFileExecutionTransferValidatorTest {

  private final MigrationFileExecutionTransferValidator validator = new MigrationFileExecutionTransferValidator();

  @ParameterizedTest
  @EnumSource(MigrationFileExecutionStatus.class)
  void givenFileExecutionStatusWhenValidateMarkTransferringThenAllowsOnlyExported(
    MigrationFileExecutionStatus status
  ) {
    MigrationFileExecution fileExecution = fileExecution(status);

    if (status == MigrationFileExecutionStatus.EXPORTED) {
      assertDoesNotThrow(() -> validator.validateMarkTransferring(fileExecution));
    } else {
      assertThrows(ConflictException.class, () -> validator.validateMarkTransferring(fileExecution));
    }

    assertEquals(status, fileExecution.getStatus());
  }

  @Test
  void givenTransferringExecutionWhenValidateRecordUploadAcceptedThenKeepsStatusUnchanged() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);

    assertDoesNotThrow(() -> validator.validateRecordUploadAccepted(
      fileExecution, 42L, UploadsStatusEnum.UPLOADED
    ));

    assertEquals(MigrationFileExecutionStatus.TRANSFERING, fileExecution.getStatus());
    assertNull(fileExecution.getUploadId());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0, -1})
  void givenInvalidUploadIdWhenValidateRecordUploadAcceptedThenThrowsInvalidValueException(Long uploadId) {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validateRecordUploadAccepted(fileExecution, uploadId, UploadsStatusEnum.UPLOADED)
    );

    assertEquals("INVALID_UPLOAD_ID", exception.getCode());
    assertEquals(MigrationFileExecutionStatus.TRANSFERING, fileExecution.getStatus());
  }

  @Test
  void givenDifferentAcceptedUploadIdWhenValidateRecordUploadAcceptedThenRejectsChange() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);
    fileExecution.setUploadId(41L);
    fileExecution.setUploadStatus(UploadsStatusEnum.UPLOADED);

    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> validator.validateRecordUploadAccepted(fileExecution, 42L, UploadsStatusEnum.UPLOADED)
    );

    assertEquals("IMMUTABLE_FIELD", exception.getCode());
    assertEquals(41L, fileExecution.getUploadId());
    assertEquals(MigrationFileExecutionStatus.TRANSFERING, fileExecution.getStatus());
  }

  @Test
  void givenMissingExtractionIdWhenValidateMarkTransferringThenThrowsInvalidValueException() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    fileExecution.setExtractionId(" ");

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validateMarkTransferring(fileExecution)
    );

    assertEquals("INVALID_MIGRATION_REQUEST", exception.getCode());
  }

  @Test
  void givenMissingFilePathWhenValidateMarkTransferringThenThrowsInvalidValueException() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    fileExecution.setFilePath("");

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validateMarkTransferring(fileExecution)
    );

    assertEquals("INVALID_MIGRATION_REQUEST", exception.getCode());
  }

  @Test
  void givenExistingUploadIdWhenValidateMarkTransferringThenThrowsConflictException() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    fileExecution.setUploadId(41L);

    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> validator.validateMarkTransferring(fileExecution)
    );

    assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
  }

  @Test
  void givenExistingUploadStatusWhenValidateMarkTransferringThenThrowsConflictException() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    fileExecution.setUploadStatus(UploadsStatusEnum.UPLOADED);

    assertThrows(ConflictException.class, () -> validator.validateMarkTransferring(fileExecution));
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = "TRANSFERING", mode = EnumSource.Mode.EXCLUDE)
  void givenNonTransferringStatusWhenValidateRecordUploadAcceptedThenThrowsConflictException(
    MigrationFileExecutionStatus status
  ) {
    MigrationFileExecution fileExecution = fileExecution(status);

    assertThrows(
      ConflictException.class,
      () -> validator.validateRecordUploadAccepted(fileExecution, 42L, UploadsStatusEnum.UPLOADED)
    );
  }

  @Test
  void givenInvalidUploadStatusWhenValidateRecordUploadAcceptedThenThrowsInvalidValueException() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validateRecordUploadAccepted(
        fileExecution,
        42L,
        UploadsStatusEnum.PROCESSING
      )
    );

    assertEquals("INVALID_UPLOAD_STATUS", exception.getCode());
  }

  @Test
  void givenSameAcceptedUploadDataWhenValidateRecordUploadAcceptedThenAllowsRetry() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);
    fileExecution.setUploadId(42L);
    fileExecution.setUploadStatus(UploadsStatusEnum.UPLOADED);

    assertDoesNotThrow(() ->
      validator.validateRecordUploadAccepted(fileExecution, 42L, UploadsStatusEnum.UPLOADED)
    );
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"EXPORTED", "TRANSFERING"})
  void givenTransferInProgressWhenValidateMarkTransferFailedThenAllowsFailure(
    MigrationFileExecutionStatus status
  ) {
    assertDoesNotThrow(() -> validator.validateMarkTransferFailed(
      fileExecution(status),
      "UPLOAD_FAILED",
      "PU rejected the upload"
    ));
  }

  @ParameterizedTest
  @EnumSource(
    value = MigrationFileExecutionStatus.class,
    names = {"EXPORTED", "TRANSFERING", "FAILED"},
    mode = EnumSource.Mode.EXCLUDE
  )
  void givenStatusWithoutFailureTransitionWhenValidateMarkTransferFailedThenThrowsConflictException(
    MigrationFileExecutionStatus status
  ) {
    MigrationFileExecution fileExecution = fileExecution(status);

    assertThrows(
      ConflictException.class,
      () -> validator.validateMarkTransferFailed(fileExecution, "UPLOAD_FAILED", "failure")
    );
  }

  @Test
  void givenBlankErrorCodeWhenValidateMarkTransferFailedThenThrowsInvalidValueException() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validateMarkTransferFailed(
        fileExecution,
        " ",
        "failure"
      )
    );

    assertEquals("INVALID_MIGRATION_REQUEST", exception.getCode());
  }

  @Test
  void givenChangedFailureMessageWhenValidateMarkTransferFailedThenRejectsChange() {
    MigrationFileExecution fileExecution = fileExecution(MigrationFileExecutionStatus.FAILED);
    fileExecution.setUploadErrorCode("UPLOAD_FAILED");
    fileExecution.setUploadErrorMsg("original failure");

    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> validator.validateMarkTransferFailed(fileExecution, "UPLOAD_FAILED", "different failure")
    );

    assertEquals("IMMUTABLE_FIELD", exception.getCode());
  }

  private MigrationFileExecution fileExecution(MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder()
      .migrationId(UUID.randomUUID())
      .fileType(MigrationFileType.ORGANIZATIONS)
      .status(status)
      .extractionId("extractor-123")
      .filePath("organizations.zip")
      .build();
  }
}
