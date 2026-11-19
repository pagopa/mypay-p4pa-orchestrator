package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
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

import static org.junit.jupiter.api.Assertions.*;

class MigrationFileExecutionEvidenceValidatorTest {

  private final MigrationFileExecutionEvidenceValidator validator = new MigrationFileExecutionEvidenceValidator();

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"WAITING_PU"}, mode = EnumSource.Mode.EXCLUDE)
  void givenNonWaitingStateWhenValidatePollingThenRejectsWithoutMutation(MigrationFileExecutionStatus status) {
    MigrationFileExecution file = file();
    file.setStatus(status);

    assertThrows(ConflictException.class, () -> validator.validatePolling(file));

    assertEquals(status, file.getStatus());
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"COMPLETED", "WARNING", "FAILED"})
  void givenWaitingFileWhenValidateRecordEvidenceThenAllowsOnlyConfirmedTerminalTargets(MigrationFileExecutionStatus target) {
    MigrationFileExecution file = file();
    Uploads upload = new Uploads().uploadId(42L).status(UploadsStatusEnum.COMPLETED);

    assertDoesNotThrow(() -> validator.validateRecordEvidence(file, upload, target));

    assertEquals(MigrationFileExecutionStatus.WAITING_PU, file.getStatus());
  }

  @ParameterizedTest
  @MethodSource("invalidFileCounts")
  void givenInvalidFileCountsWhenValidateUploadThenRejectsEvidence(Integer total, Integer processed) {
    MigrationFileExecution file = file();
    Uploads upload = new Uploads().uploadId(42L).status(UploadsStatusEnum.COMPLETED)
      .numTotalFiles(total).numCorrectlyProcessedFiles(processed);

    assertThrows(InvalidValueException.class, () -> validator.validateUpload(file, 42L, upload));
  }

  static Stream<Arguments> invalidFileCounts() {
    return Stream.of(Arguments.of(-1, 0), Arguments.of(1, -1), Arguments.of(1, 2));
  }

  @Test
  void givenDifferentUploadWhenValidateUploadThenRejectsEvidence() {
    MigrationFileExecution file = file();
    Uploads upload = new Uploads().uploadId(43L).status(UploadsStatusEnum.COMPLETED);

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> validator.validateUpload(file, 42L, upload));

    assertEquals("EVIDENCE_INVALID_RESPONSE", failure.getCode());
    assertEquals("PU returned a missing status or a different upload ID for upload 42", failure.getMessage());
  }

  @ParameterizedTest
  @MethodSource("invalidDetailIdentifiers")
  void givenInvalidDetailIdentifierWhenValidateDetailsThenRejectsEvidence(UploadDetails detail) {
    List<UploadDetails> details = Collections.singletonList(detail);

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> validator.validateDetails(42L, details));

    assertEquals("EVIDENCE_INVALID_RESPONSE", failure.getCode());
    assertEquals("PU returned invalid or duplicate details for upload 42", failure.getMessage());
  }

  static Stream<Arguments> invalidDetailIdentifiers() {
    return Stream.of(
      Arguments.of((UploadDetails) null),
      Arguments.of(new UploadDetails().uploadId(43L).uploadDetailId(1L)
        .fileSize(1L).status(IngestionFlowFileStatus.COMPLETED)),
      Arguments.of(new UploadDetails().uploadId(42L).fileSize(1L).status(IngestionFlowFileStatus.COMPLETED)),
      Arguments.of(new UploadDetails().uploadId(42L).uploadDetailId(0L)
        .fileSize(1L).status(IngestionFlowFileStatus.COMPLETED))
    );
  }

  @Test
  void givenSameFileWithDifferentIdentifiersWhenValidateDetailsThenRejectsDuplicateCorrelation() {
    UploadDetails first = new UploadDetails().uploadId(42L).uploadDetailId(1L)
      .fileName("IPA_TEST_2-organizations.csv");
    UploadDetails second = new UploadDetails().uploadId(42L).uploadDetailId(2L)
      .fileName(first.getFileName());
    List<UploadDetails> details = List.of(first, second);

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> validator.validateDetails(42L, details));

    assertEquals("EVIDENCE_INVALID_RESPONSE", failure.getCode());
  }

  @ParameterizedTest
  @MethodSource("invalidDetailUpdates")
  void givenInvalidPuDetailWhenValidateDetailUpdateThenRejectsWithoutChangingExport(UploadDetails detail) {
    MigrationFileExecutionDetail original = MigrationFileExecutionDetail.builder()
      .fileName("IPA_TEST_2-organizations.csv").ipaCode("IPA_TEST_2").fileSize(500L)
      .status(MigrationFileExecutionDetailStatus.WAITING_PU).build();
    MigrationFileExecution file = file();

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> validator.validateDetailUpdate(
      file, original, detail
    ));

    assertEquals("EVIDENCE_INVALID_RESPONSE", failure.getCode());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, original.getStatus());
    assertEquals("IPA_TEST_2", original.getIpaCode());
    assertNull(original.getUploadDetailId());
    assertNull(original.getNumTotalRows());
  }

  static Stream<Arguments> invalidDetailUpdates() {
    return Stream.of(
      Arguments.of(puDetail().fileName("IPA_TEST_3-organizations.csv")),
      Arguments.of(puDetail().fileSize(501L)),
      Arguments.of(puDetail().numTotalRows(-1L)),
      Arguments.of(puDetail().numCorrectlyImportedRows(-1L)),
      Arguments.of(puDetail().numCorrectlyImportedRows(11L)),
      Arguments.of(puDetail().numTotalRows(Long.MAX_VALUE)),
      Arguments.of(puDetail().numCorrectlyImportedRows(Long.MAX_VALUE))
    );
  }

  @ParameterizedTest
  @MethodSource("regressingUploadStatuses")
  void givenRegressingUploadWhenValidateRecordEvidenceThenRejectsWithoutMutation(
    UploadsStatusEnum current, UploadsStatusEnum incoming
  ) {
    MigrationFileExecution file = file();
    file.setUploadStatus(current);
    Uploads upload = new Uploads().uploadId(42L).status(incoming);

    ConflictException failure = assertThrows(ConflictException.class,
      () -> validator.validateRecordEvidence(file, upload, MigrationFileExecutionStatus.WAITING_PU));

    assertEquals("EVIDENCE_CONFLICT", failure.getCode());
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
  void givenDifferentUploadWhenValidateMarkFailedThenRejectsWithoutMutation(MigrationFileExecutionStatus status) {
    MigrationFileExecution file = file();
    file.setStatus(status);

    ConflictException failure = assertThrows(ConflictException.class, () -> validator.validateMarkFailed(file, 43L));

    assertEquals("EVIDENCE_CONFLICT", failure.getCode());
    assertEquals(42L, file.getUploadId());
    assertEquals(status, file.getStatus());
  }

  @ParameterizedTest
  @MethodSource("unchangedDetailStatuses")
  void givenUnchangedTerminalSnapshotWhenValidateDetailUpdateThenPreservesExistingOutcome(
    MigrationFileExecutionDetailStatus local, IngestionFlowFileStatus remote
  ) {
    MigrationFileExecution file = file();
    file.setStatus(MigrationFileExecutionStatus.FAILED);
    MigrationFileExecutionDetail original = MigrationFileExecutionDetail.builder()
      .fileName("IPA_TEST_2-organizations.csv").fileSize(500L).uploadDetailId(123L)
      .status(local).numTotalRows(10).numCorrectlyImportedRows(10).build();
    UploadDetails updated = puDetail().status(remote);

    assertDoesNotThrow(() -> validator.validateDetailUpdate(file, original, updated));

    assertEquals(local, original.getStatus());
    assertEquals(123L, original.getUploadDetailId());
  }

  static Stream<Arguments> unchangedDetailStatuses() {
    return Stream.of(
      Arguments.of(MigrationFileExecutionDetailStatus.WAITING_PU, IngestionFlowFileStatus.UPLOADED),
      Arguments.of(MigrationFileExecutionDetailStatus.WAITING_PU, IngestionFlowFileStatus.PROCESSING),
      Arguments.of(MigrationFileExecutionDetailStatus.WAITING_PU, IngestionFlowFileStatus.WAITING_FILE),
      Arguments.of(MigrationFileExecutionDetailStatus.COMPLETED, IngestionFlowFileStatus.COMPLETED),
      Arguments.of(MigrationFileExecutionDetailStatus.PARTIAL, IngestionFlowFileStatus.WARNING),
      Arguments.of(MigrationFileExecutionDetailStatus.FAILED, IngestionFlowFileStatus.ERROR)
    );
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
