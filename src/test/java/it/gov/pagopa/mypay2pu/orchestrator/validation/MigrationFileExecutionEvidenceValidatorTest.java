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
  void givenWaitingFileWhenValidateTransitionThenAllowsOnlyConfirmedTerminalTargets(MigrationFileExecutionStatus target) {
    MigrationFileExecution file = file();

    assertDoesNotThrow(() -> validator.validateTransition(file, target));

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
    List<UploadDetails> details = List.of(detail);

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> validator.validateDetails(42L, details));

    assertEquals("EVIDENCE_INVALID_RESPONSE", failure.getCode());
    assertEquals("PU returned invalid or duplicate details for upload 42", failure.getMessage());
  }

  static Stream<Arguments> invalidDetailIdentifiers() {
    return Stream.of(
      Arguments.of(new UploadDetails().uploadId(42L).fileSize(1L).status(IngestionFlowFileStatus.COMPLETED)),
      Arguments.of(new UploadDetails().uploadId(42L).uploadDetailId(0L)
        .fileSize(1L).status(IngestionFlowFileStatus.COMPLETED))
    );
  }

  private MigrationFileExecution file() {
    return MigrationFileExecution.builder().status(MigrationFileExecutionStatus.WAITING_PU).uploadId(42L)
      .uploadCompletedAt(LocalDateTime.of(2026, Month.NOVEMBER, 18, 12, 0)).build();
  }
}
