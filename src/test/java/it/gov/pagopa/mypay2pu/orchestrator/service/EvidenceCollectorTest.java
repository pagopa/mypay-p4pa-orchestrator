package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.EvidencePollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.EvidenceException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionEvidenceValidator;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.ResourceAccessException;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceCollectorTest {

  private static final UUID MIGRATION_ID = UUID.fromString("604b4042-a027-4b07-a34e-83026c166701");
  private static final UUID FILE_ID = UUID.fromString("2f980de8-3891-4f69-96b8-f95de4f1d679");
  private static final MigrationFileType FILE_TYPE = MigrationFileType.ORGANIZATIONS;
  private static final LocalDateTime ACCEPTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 18, 12, 0);
  private static final Instant NOW = ACCEPTED_AT.toInstant(ZoneOffset.UTC);
  @Mock private MigrationFileExecutionEvidenceService evidenceServiceMock;
  @Mock private PuMigrationService puMigrationServiceMock;

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(evidenceServiceMock, puMigrationServiceMock);
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"COMPLETED", "WARNING"})
  void givenTerminalFileWhenCollectThenDoesNotPollAgain(MigrationFileExecutionStatus status) {
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(status));

    collector(Clock.fixed(NOW, ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenFailedTerminalFileWhenCollectThenPropagatesPersistedErrorWithoutPolling() {
    MigrationFileExecution failed = file(FAILED);
    failed.setUploadErrorCode("EVIDENCE_TIMEOUT");
    failed.setUploadErrorMsg("Upload timed out");
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(failed);

    EvidenceCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_TIMEOUT", failure.getCode());
    assertEquals("Upload timed out", failure.getMessage());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenAcceptedUploadWhenCollectThenPollsUntilEvidenceCompletes() {
    MigrationFileExecution pending = file(WAITING_PU);
    MigrationFileExecution completed = file(COMPLETED);
    Uploads processing = upload(UploadsStatusEnum.PROCESSING);
    Uploads done = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of();
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(pending);
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(processing, done);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(details);
    when(evidenceServiceMock.recordEvidence(FILE_ID, 42L, processing, details)).thenReturn(pending);
    when(evidenceServiceMock.recordEvidence(FILE_ID, 42L, done, details)).thenReturn(completed);

    collector(Clock.fixed(NOW, ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock, times(2)).getMigrationUpload(42L);
    verify(puMigrationServiceMock, times(2)).getMigrationUploadDetails(42L);
    verify(evidenceServiceMock).recordEvidence(FILE_ID, 42L, processing, details);
    verify(evidenceServiceMock).recordEvidence(FILE_ID, 42L, done, details);
  }

  @Test
  void givenExpiredAcceptedUploadWhenCollectThenTimesOutWithoutAnotherRemoteCall() {
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    expectFailure("EVIDENCE_TIMEOUT", "Timed out waiting for PU evidence for upload 42");

    EvidenceCollector collector = collector(Clock.fixed(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_TIMEOUT", failure.getCode());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenResponseArrivingAtDeadlineWhenCollectThenDoesNotPersistSuccess() {
    TestClock clock = new TestClock();
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    expectFailure("EVIDENCE_TIMEOUT", "Timed out waiting for PU evidence for upload 42");
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenAnswer(invocation -> {
      clock.now = NOW.plus(Duration.ofHours(12));
      return upload(UploadsStatusEnum.COMPLETED);
    });

    EvidenceCollector collector = collector(clock);
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_TIMEOUT", failure.getCode());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenExhaustedHttpRetriesWhenCollectThenPersistsPollingFailureWithoutRetrying() {
    ResourceAccessException cause = new ResourceAccessException("PU unavailable");
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenThrow(cause);
    expectFailure("EVIDENCE_POLLING_FAILED", "PU evidence request failed for upload 42");

    EvidenceCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_POLLING_FAILED", failure.getCode());
    assertSame(cause, failure.getCause());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenPuErrorWhenCollectThenPersistsFailureWithoutRequiringDetailsEndpoint() {
    MigrationFileExecution failed = file(FAILED);
    failed.setUploadErrorCode("EVIDENCE_PU_FAILED");
    failed.setUploadErrorMsg("PU rejected upload");
    Uploads upload = upload(UploadsStatusEnum.ERROR);
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload);
    when(evidenceServiceMock.recordEvidence(FILE_ID, 42L, upload, List.of())).thenReturn(failed);
    expectFailure("EVIDENCE_PU_FAILED", "PU rejected upload");

    EvidenceCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_PU_FAILED", failure.getCode());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(evidenceServiceMock).recordEvidence(FILE_ID, 42L, upload, List.of());
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenDifferentUploadResponseWhenCollectThenRejectsBeforeFetchingDetails() {
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload(UploadsStatusEnum.COMPLETED).uploadId(43L));
    expectFailure("EVIDENCE_INVALID_RESPONSE", "PU returned a missing status or a different upload ID for upload 42");

    EvidenceCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_INVALID_RESPONSE", failure.getCode());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenInvalidDetailMappingWhenCollectThenRecordsEvidenceErrorAndPreservesCause() {
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    InvalidValueException cause = new InvalidValueException("TRANSFER_INVALID_UPLOAD_DETAILS", "Invalid row counts");
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(List.of());
    when(evidenceServiceMock.recordEvidence(FILE_ID, 42L, upload, List.of())).thenThrow(cause);
    expectFailure("EVIDENCE_INVALID_RESPONSE", "Invalid row counts");

    EvidenceCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_INVALID_RESPONSE", failure.getCode());
    assertSame(cause, failure.getCause());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    verify(evidenceServiceMock).recordEvidence(FILE_ID, 42L, upload, List.of());
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenInterruptedWaitWhenCollectThenRestoresInterruptFlagAndFailsExplicitly() {
    MigrationFileExecution pending = file(WAITING_PU);
    Uploads upload = upload(UploadsStatusEnum.PROCESSING);
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(pending);
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(List.of());
    when(evidenceServiceMock.recordEvidence(FILE_ID, 42L, upload, List.of())).thenReturn(pending);
    expectFailure("EVIDENCE_INTERRUPTED", "Interrupted while collecting evidence for upload 42");
    EvidenceCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    Thread.currentThread().interrupt();
    try {
      EvidenceException failure = assertThrows(EvidenceException.class,
        () -> collector.collect(MIGRATION_ID, FILE_TYPE));

      assertEquals("EVIDENCE_INTERRUPTED", failure.getCode());
      assertTrue(Thread.currentThread().isInterrupted());
      verify(evidenceServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
    } finally {
      Thread.interrupted();
    }
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    verify(evidenceServiceMock).recordEvidence(FILE_ID, 42L, upload, List.of());
  }

  @Test
  void givenFailurePersistenceErrorWhenCollectThenPreservesOriginalFailureAndCause() {
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    DataAccessResourceFailureException persistenceFailure = new DataAccessResourceFailureException("DB unavailable");
    String message = "Timed out waiting for PU evidence for upload 42";
    doThrow(persistenceFailure).when(evidenceServiceMock).markFailed(FILE_ID, 42L, "EVIDENCE_TIMEOUT", message);

    EvidenceCollector collector = collector(Clock.fixed(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_TIMEOUT", failure.getCode());
    assertArrayEquals(new Throwable[]{persistenceFailure}, failure.getSuppressed());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, "EVIDENCE_TIMEOUT", message);
  }

  @Test
  void givenConcurrentCompletionAtTimeoutWhenCollectThenPreservesSuccessfulOutcome() {
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    String message = "Timed out waiting for PU evidence for upload 42";
    when(evidenceServiceMock.markFailed(FILE_ID, 42L, "EVIDENCE_TIMEOUT", message)).thenReturn(file(COMPLETED));

    collector(Clock.fixed(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, "EVIDENCE_TIMEOUT", message);
  }

  @Test
  void givenConcurrentPuFailureAtTimeoutWhenCollectThenPropagatesPersistedOutcome() {
    MigrationFileExecution failed = file(FAILED);
    failed.setUploadErrorCode("EVIDENCE_PU_FAILED");
    failed.setUploadErrorMsg("PU rejected upload");
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    String message = "Timed out waiting for PU evidence for upload 42";
    when(evidenceServiceMock.markFailed(FILE_ID, 42L, "EVIDENCE_TIMEOUT", message)).thenReturn(failed);

    EvidenceCollector collector = collector(Clock.fixed(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_PU_FAILED", failure.getCode());
    assertEquals("PU rejected upload", failure.getMessage());
    EvidenceException timeout = assertInstanceOf(EvidenceException.class, failure.getCause());
    assertEquals("EVIDENCE_TIMEOUT", timeout.getCode());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, "EVIDENCE_TIMEOUT", message);
  }

  @Test
  void givenConcurrentTerminalUpdateWhenCollectThenRereadsOutcomeWithoutRegressingIt() {
    Uploads upload = upload(UploadsStatusEnum.PROCESSING);
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU), file(WARNING));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(List.of());
    when(evidenceServiceMock.recordEvidence(FILE_ID, 42L, upload, List.of()))
      .thenThrow(new ConflictException("EVIDENCE_CONFLICT", "terminal detail changed"));

    collector(Clock.fixed(NOW, ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    verify(evidenceServiceMock, times(2)).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    verify(evidenceServiceMock).recordEvidence(FILE_ID, 42L, upload, List.of());
  }

  @Test
  void givenMissingAcceptanceTimestampWhenCollectThenFailsBeforePolling() {
    MigrationFileExecution file = file(WAITING_PU);
    file.setUploadCompletedAt(null);
    when(evidenceServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file);
    String message = "Evidence requires a positive uploadId and uploadCompletedAt";
    expectFailure("EVIDENCE_INVALID_RESPONSE", message);

    EvidenceCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    EvidenceException failure = assertThrows(EvidenceException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("EVIDENCE_INVALID_RESPONSE", failure.getCode());
    verify(evidenceServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(evidenceServiceMock).markFailed(FILE_ID, 42L, "EVIDENCE_INVALID_RESPONSE", message);
  }

  private EvidenceCollector collector(Clock clock) {
    return new EvidenceCollector(evidenceServiceMock, puMigrationServiceMock,
      new MigrationFileExecutionEvidenceValidator(),
      new EvidencePollingProperties(Duration.ofSeconds(1), Duration.ofHours(12)), clock);
  }

  private void expectFailure(String code, String message) {
    when(evidenceServiceMock.markFailed(FILE_ID, 42L, code, message)).thenReturn(file(FAILED));
  }

  private MigrationFileExecution file(MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder().id(FILE_ID).migrationId(MIGRATION_ID).fileType(FILE_TYPE)
      .status(status).uploadId(42L).uploadCompletedAt(ACCEPTED_AT).build();
  }

  private Uploads upload(UploadsStatusEnum status) {
    return new Uploads().uploadId(42L).status(status);
  }

  private static class TestClock extends Clock {
    private Instant now = NOW;

    @Override public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override public Clock withZone(ZoneId zone) {
      return Clock.fixed(now, zone);
    }

    @Override public Instant instant() {
      return now;
    }
  }
}
