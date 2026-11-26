package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MigrationFileExecutionTransferOutcomeValidatorTest {

  private final MigrationFileExecutionTransferOutcomeValidator validator = new MigrationFileExecutionTransferOutcomeValidator();

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"WAITING_PU"}, mode = EnumSource.Mode.EXCLUDE)
  void givenNonWaitingStateWhenValidatePollingThenRejectsWithoutMutation(MigrationFileExecutionStatus status) {
    MigrationFileExecution file = file();
    file.setStatus(status);

    assertThrows(ConflictException.class, () -> validator.validatePolling(file));

    assertEquals(status, file.getStatus());
  }

  @Test
  void givenWaitingFileWhenValidateRecordTransferOutcomeThenAllowsPuUpdate() {
    MigrationFileExecution file = file();
    Uploads upload = new Uploads().uploadId(42L).status(UploadsStatusEnum.COMPLETED);

    assertDoesNotThrow(() -> validator.validateRecordTransferOutcome(file, upload));

    assertEquals(MigrationFileExecutionStatus.WAITING_PU, file.getStatus());
  }

  @Test
  void givenDifferentUploadWhenValidateUploadThenRejectsTransferOutcome() {
    MigrationFileExecution file = file();
    Uploads upload = new Uploads().uploadId(43L).status(UploadsStatusEnum.COMPLETED);

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> validator.validateUpload(file, upload));

    assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    assertEquals("PU returned a different upload ID for upload 42", failure.getMessage());
  }

  @ParameterizedTest
  @MethodSource("invalidDetailIdentifiers")
  void givenInvalidDetailIdentifierWhenValidateDetailsThenRejectsTransferOutcome(UploadDetails detail) {
    List<UploadDetails> details = Collections.singletonList(detail);

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> validator.validateDetails(42L, details));

    assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    assertEquals("PU returned invalid details for upload 42", failure.getMessage());
  }

  static Stream<Arguments> invalidDetailIdentifiers() {
    return Stream.of(
      Arguments.of(puDetail().uploadId(43L)),
      Arguments.of(puDetail().uploadDetailId(null))
    );
  }

  @Test
  void givenSameFileWithDifferentIdentifiersWhenValidateDetailsThenDefersCorrelationToService() {
    UploadDetails first = new UploadDetails().uploadId(42L).uploadDetailId(1L)
      .fileName("IPA_TEST_2-organizations.csv").fileSize(500L).status(IngestionFlowFileStatus.PROCESSING);
    UploadDetails second = new UploadDetails().uploadId(42L).uploadDetailId(2L)
      .fileName(first.getFileName()).fileSize(600L).status(IngestionFlowFileStatus.PROCESSING);
    List<UploadDetails> details = List.of(first, second);

    assertDoesNotThrow(() -> validator.validateDetails(42L, details));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void givenBlankFileNameWhenValidateDetailsThenRejectsTransferOutcome(String fileName) {
    List<UploadDetails> snapshot = List.of(puDetail().fileName(fileName));

    assertThrows(InvalidValueException.class, () -> validator.validateDetails(42L, snapshot));
  }

  @ParameterizedTest
  @MethodSource("regressingUploadStatuses")
  void givenRegressingUploadWhenValidateRecordTransferOutcomeThenRejectsWithoutMutation(
    UploadsStatusEnum current, UploadsStatusEnum incoming
  ) {
    MigrationFileExecution file = file();
    file.setUploadStatus(current);
    Uploads upload = new Uploads().uploadId(42L).status(incoming);

    ConflictException failure = assertThrows(ConflictException.class,
      () -> validator.validateRecordTransferOutcome(file, upload));

    assertEquals("TRANSFER_OUTCOME_CONFLICT", failure.getCode());
    assertEquals(current, file.getUploadStatus());
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, file.getStatus());
  }

  static Stream<Arguments> regressingUploadStatuses() {
    return Stream.of(
      Arguments.of(UploadsStatusEnum.PROCESSING, UploadsStatusEnum.UPLOADED),
      Arguments.of(UploadsStatusEnum.COMPLETED, UploadsStatusEnum.UPLOADED),
      Arguments.of(UploadsStatusEnum.COMPLETED, UploadsStatusEnum.PROCESSING)
    );
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"WAITING_PU", "COMPLETED", "WARNING", "FAILED"})
  void givenWaitingOrTerminalStateWhenValidateMarkFailedThenPreservesPersistedUpload(MigrationFileExecutionStatus status) {
    MigrationFileExecution file = file();
    file.setStatus(status);

    assertDoesNotThrow(() -> validator.validateMarkFailed(file));

    assertEquals(42L, file.getUploadId());
    assertEquals(status, file.getStatus());
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"WAITING_PU", "COMPLETED", "WARNING", "FAILED"},
    mode = EnumSource.Mode.EXCLUDE)
  void givenInvalidStateWhenValidateMarkFailedThenRejectsWithoutMutation(MigrationFileExecutionStatus status) {
    MigrationFileExecution file = file();
    file.setStatus(status);

    ConflictException failure = assertThrows(ConflictException.class, () -> validator.validateMarkFailed(file));

    assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", failure.getCode());
    assertEquals(42L, file.getUploadId());
    assertEquals(status, file.getStatus());
  }

  private static UploadDetails puDetail() {
    return new UploadDetails().uploadId(42L).uploadDetailId(123L).fileName("IPA_TEST_2-organizations.csv")
      .fileSize(500L).status(IngestionFlowFileStatus.COMPLETED).numTotalRows(10L).numCorrectlyImportedRows(10L);
  }

  private MigrationFileExecution file() {
    return MigrationFileExecution.builder().status(MigrationFileExecutionStatus.WAITING_PU).uploadId(42L)
      .uploadCompletedAt(LocalDateTime.of(2026, Month.NOVEMBER, 18, 12, 0)).build();
  }
}
