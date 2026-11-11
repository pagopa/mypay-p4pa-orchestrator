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
