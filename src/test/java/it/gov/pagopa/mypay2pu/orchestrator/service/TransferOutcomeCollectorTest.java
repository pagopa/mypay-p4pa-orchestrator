package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.TransferOutcomePollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferOutcomeException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferOutcomeValidator;
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
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.ResourceAccessException;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferOutcomeCollectorTest {

  private static final UUID MIGRATION_ID = UUID.fromString("604b4042-a027-4b07-a34e-83026c166701");
  private static final UUID FILE_ID = UUID.fromString("2f980de8-3891-4f69-96b8-f95de4f1d679");
  private static final MigrationFileType FILE_TYPE = MigrationFileType.ORGANIZATIONS;
  private static final LocalDateTime ACCEPTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 18, 12, 0);
  private static final Instant NOW = ACCEPTED_AT.toInstant(ZoneOffset.UTC);
  @Mock private MigrationFileExecutionTransferOutcomeService transferOutcomeServiceMock;
  @Mock private PuMigrationService puMigrationServiceMock;

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(transferOutcomeServiceMock, puMigrationServiceMock);
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"COMPLETED", "WARNING"})
  void givenTerminalFileWhenCollectThenDoesNotPollAgain(MigrationFileExecutionStatus status) {
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(status));

    collector(Clock.fixed(NOW, ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenFailedTerminalFileWhenCollectThenPropagatesPersistedErrorWithoutPolling() {
    MigrationFileExecution failed = file(FAILED);
    failed.setUploadErrorCode("TRANSFER_OUTCOME_TIMEOUT");
    failed.setUploadErrorMsg("Upload timed out");
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(failed);

    TransferOutcomeCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_TIMEOUT", failure.getCode());
    assertEquals("Upload timed out", failure.getMessage());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenAcceptedUploadWhenCollectThenPollsUntilTransferOutcomeCompletes() {
    MigrationFileExecution pending = file(WAITING_PU);
    MigrationFileExecution completed = file(COMPLETED);
    Uploads processing = upload(UploadsStatusEnum.PROCESSING);
    Uploads done = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of();
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(pending);
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(processing, done);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(details);
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, processing, details)).thenReturn(pending);
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, done, details)).thenReturn(completed);

    collector(Clock.fixed(NOW, ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock, times(2)).getMigrationUpload(42L);
    verify(puMigrationServiceMock, times(2)).getMigrationUploadDetails(42L);
    verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, processing, details);
    verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, done, details);
  }

  @Test
  void givenExpiredAcceptedUploadWhenCollectThenTimesOutWithoutAnotherRemoteCall() {
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    expectFailure("TRANSFER_OUTCOME_TIMEOUT", "Timed out waiting for PU transfer outcome for upload 42");

    TransferOutcomeCollector collector = collector(Clock.fixed(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_TIMEOUT", failure.getCode());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenResponseArrivingAtDeadlineWhenCollectThenDoesNotPersistSuccess() {
    TestClock clock = new TestClock();
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    expectFailure("TRANSFER_OUTCOME_TIMEOUT", "Timed out waiting for PU transfer outcome for upload 42");
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenAnswer(invocation -> {
      clock.now = NOW.plus(Duration.ofHours(12));
      return upload(UploadsStatusEnum.COMPLETED);
    });

    TransferOutcomeCollector collector = collector(clock);
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_TIMEOUT", failure.getCode());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenExhaustedHttpRetriesWhenCollectThenPersistsPollingFailureWithoutRetrying() {
    ResourceAccessException cause = new ResourceAccessException("PU unavailable");
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenThrow(cause);
    expectFailure("TRANSFER_OUTCOME_POLLING_FAILED", "PU transfer outcome request failed for upload 42");

    TransferOutcomeCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_POLLING_FAILED", failure.getCode());
    assertSame(cause, failure.getCause());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenPuErrorWhenCollectThenPersistsFailureWithoutRequiringDetailsEndpoint() {
    MigrationFileExecution failed = file(FAILED);
    failed.setUploadErrorCode("TRANSFER_OUTCOME_PU_FAILED");
    failed.setUploadErrorMsg("PU rejected upload");
    Uploads upload = upload(UploadsStatusEnum.ERROR);
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload);
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, upload, List.of())).thenReturn(failed);
    expectFailure("TRANSFER_OUTCOME_PU_FAILED", "PU rejected upload");

    TransferOutcomeCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_PU_FAILED", failure.getCode());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, upload, List.of());
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenDifferentUploadResponseWhenCollectThenRejectsBeforeFetchingDetails() {
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload(UploadsStatusEnum.COMPLETED).uploadId(43L));
    expectFailure("TRANSFER_OUTCOME_INVALID_RESPONSE", "PU returned a missing status or a different upload ID for upload 42");

    TransferOutcomeCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenInvalidDetailMappingWhenCollectThenRecordsTransferOutcomeErrorAndPreservesCause() {
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    InvalidValueException cause = new InvalidValueException("TRANSFER_INVALID_UPLOAD_DETAILS", "Invalid row counts");
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(List.of());
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, upload, List.of())).thenThrow(cause);
    expectFailure("TRANSFER_OUTCOME_INVALID_RESPONSE", "Invalid row counts");

    TransferOutcomeCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    assertSame(cause, failure.getCause());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, upload, List.of());
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
  }

  @Test
  void givenInterruptedWaitWhenCollectThenRestoresInterruptFlagAndFailsExplicitly() {
    MigrationFileExecution pending = file(WAITING_PU);
    Uploads upload = upload(UploadsStatusEnum.PROCESSING);
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(pending);
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(List.of());
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, upload, List.of())).thenReturn(pending);
    expectFailure("TRANSFER_OUTCOME_INTERRUPTED", "Interrupted while collecting transfer outcome for upload 42");
    TransferOutcomeCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    Thread.currentThread().interrupt();
    try {
      TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
        () -> collector.collect(MIGRATION_ID, FILE_TYPE));

      assertEquals("TRANSFER_OUTCOME_INTERRUPTED", failure.getCode());
      assertTrue(Thread.currentThread().isInterrupted());
      verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, failure.getCode(), failure.getMessage());
    } finally {
      Thread.interrupted();
    }
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, upload, List.of());
  }

  @Test
  void givenFailurePersistenceErrorWhenCollectThenPreservesOriginalFailureAndCause() {
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    DataAccessResourceFailureException persistenceFailure = new DataAccessResourceFailureException("DB unavailable");
    String message = "Timed out waiting for PU transfer outcome for upload 42";
    doThrow(persistenceFailure).when(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_TIMEOUT", message);

    TransferOutcomeCollector collector = collector(Clock.fixed(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_TIMEOUT", failure.getCode());
    assertArrayEquals(new Throwable[]{persistenceFailure}, failure.getSuppressed());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_TIMEOUT", message);
  }

  @Test
  void givenConcurrentCompletionAtTimeoutWhenCollectThenPreservesSuccessfulOutcome() {
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    String message = "Timed out waiting for PU transfer outcome for upload 42";
    when(transferOutcomeServiceMock.markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_TIMEOUT", message)).thenReturn(file(COMPLETED));

    collector(Clock.fixed(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_TIMEOUT", message);
  }

  @Test
  void givenConcurrentPuFailureAtTimeoutWhenCollectThenPropagatesPersistedOutcome() {
    MigrationFileExecution failed = file(FAILED);
    failed.setUploadErrorCode("TRANSFER_OUTCOME_PU_FAILED");
    failed.setUploadErrorMsg("PU rejected upload");
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    String message = "Timed out waiting for PU transfer outcome for upload 42";
    when(transferOutcomeServiceMock.markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_TIMEOUT", message)).thenReturn(failed);

    TransferOutcomeCollector collector = collector(Clock.fixed(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_PU_FAILED", failure.getCode());
    assertEquals("PU rejected upload", failure.getMessage());
    TransferOutcomeException timeout = assertInstanceOf(TransferOutcomeException.class, failure.getCause());
    assertEquals("TRANSFER_OUTCOME_TIMEOUT", timeout.getCode());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_TIMEOUT", message);
  }

  @Test
  void givenConcurrentTerminalUpdateWhenCollectThenRereadsOutcomeWithoutRegressingIt() {
    Uploads upload = upload(UploadsStatusEnum.PROCESSING);
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU), file(WARNING));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(List.of());
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, upload, List.of()))
      .thenThrow(new ConflictException("TRANSFER_OUTCOME_CONFLICT", "terminal detail changed"));

    collector(Clock.fixed(NOW, ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    verify(transferOutcomeServiceMock, times(2)).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(puMigrationServiceMock).getMigrationUpload(42L);
    verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, upload, List.of());
  }

  @Test
  void givenMissingAcceptanceTimestampWhenCollectThenFailsBeforePolling() {
    MigrationFileExecution file = file(WAITING_PU);
    file.setUploadCompletedAt(null);
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file);
    String message = "Transfer outcome requires a positive uploadId and uploadCompletedAt";
    expectFailure("TRANSFER_OUTCOME_INVALID_RESPONSE", message);

    TransferOutcomeCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));
    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_INVALID_RESPONSE", message);
  }

  @Test
  void givenUploadedAndProcessingUploadWhenCollectThenFetchesDetailsFromProcessing() {
    MigrationFileExecution pending = file(WAITING_PU);
    Uploads uploaded = upload(UploadsStatusEnum.UPLOADED);
    Uploads processing = upload(UploadsStatusEnum.PROCESSING);
    Uploads completed = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of(new UploadDetails().uploadId(42L).uploadDetailId(43L));
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(pending);
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(uploaded, processing, completed);
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, uploaded, List.of())).thenReturn(pending);
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, processing, details)).thenReturn(pending);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(details);
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, completed, details)).thenReturn(file(COMPLETED));

    collector(Clock.fixed(NOW, ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    InOrder order = inOrder(transferOutcomeServiceMock, puMigrationServiceMock);
    order.verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    order.verify(puMigrationServiceMock).getMigrationUpload(42L);
    order.verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, uploaded, List.of());
    order.verify(puMigrationServiceMock).getMigrationUpload(42L);
    order.verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    order.verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, processing, details);
    order.verify(puMigrationServiceMock).getMigrationUpload(42L);
    order.verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    order.verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, completed, details);
  }

  @Test
  void givenAlreadyCompletedUploadWhenCollectThenFetchesDetailsWithoutObservingProcessing() {
    Uploads completed = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of(new UploadDetails().uploadId(42L).uploadDetailId(43L));
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(file(WAITING_PU));
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(completed);
    when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(details);
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, completed, details)).thenReturn(file(COMPLETED));

    collector(Clock.fixed(NOW, ZoneOffset.UTC)).collect(MIGRATION_ID, FILE_TYPE);

    InOrder order = inOrder(transferOutcomeServiceMock, puMigrationServiceMock);
    order.verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    order.verify(puMigrationServiceMock).getMigrationUpload(42L);
    order.verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    order.verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, completed, details);
  }

  @ParameterizedTest
  @EnumSource(value = UploadsStatusEnum.class, names = {"UPLOADED", "PROCESSING"})
  void givenPendingUploadThenPuErrorWhenCollectThenFailsWithoutFetchingErrorDetails(UploadsStatusEnum status) {
    MigrationFileExecution pending = file(WAITING_PU);
    MigrationFileExecution failed = file(FAILED);
    failed.setUploadErrorCode("TRANSFER_OUTCOME_PU_FAILED");
    failed.setUploadErrorMsg("PU rejected upload");
    Uploads initial = upload(status);
    Uploads error = upload(UploadsStatusEnum.ERROR);
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(pending);
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(initial, error);
    if (status == UploadsStatusEnum.PROCESSING) {
      when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(List.of());
    }
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, initial, List.of())).thenReturn(pending);
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, error, List.of())).thenReturn(failed);
    when(transferOutcomeServiceMock.markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_PU_FAILED", "PU rejected upload"))
      .thenReturn(failed);
    TransferOutcomeCollector collector = collector(Clock.fixed(NOW, ZoneOffset.UTC));

    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_PU_FAILED", failure.getCode());
    InOrder order = inOrder(transferOutcomeServiceMock, puMigrationServiceMock);
    order.verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    order.verify(puMigrationServiceMock).getMigrationUpload(42L);
    if (status == UploadsStatusEnum.PROCESSING) {
      order.verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    }
    order.verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, initial, List.of());
    order.verify(puMigrationServiceMock).getMigrationUpload(42L);
    order.verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, error, List.of());
    order.verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_PU_FAILED", "PU rejected upload");
  }

  @ParameterizedTest
  @EnumSource(value = UploadsStatusEnum.class, names = {"UPLOADED", "PROCESSING"})
  void givenPendingUploadAtDeadlineWhenCollectThenTimesOutWhilePollingStatus(UploadsStatusEnum status) {
    TestClock clock = new TestClock();
    MigrationFileExecution pending = file(WAITING_PU);
    Uploads upload = upload(status);
    String message = "Timed out waiting for PU transfer outcome for upload 42";
    when(transferOutcomeServiceMock.getRequired(MIGRATION_ID, FILE_TYPE)).thenReturn(pending);
    when(puMigrationServiceMock.getMigrationUpload(42L)).thenReturn(upload);
    if (status == UploadsStatusEnum.PROCESSING) {
      when(puMigrationServiceMock.getMigrationUploadDetails(42L)).thenReturn(List.of());
    }
    when(transferOutcomeServiceMock.recordTransferOutcome(FILE_ID, 42L, upload, List.of())).thenAnswer(invocation -> {
      clock.now = NOW.plus(Duration.ofHours(12));
      return pending;
    });
    when(transferOutcomeServiceMock.markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_TIMEOUT", message)).thenReturn(file(FAILED));
    TransferOutcomeCollector collector = collector(clock);

    TransferOutcomeException failure = assertThrows(TransferOutcomeException.class,
      () -> collector.collect(MIGRATION_ID, FILE_TYPE));

    assertEquals("TRANSFER_OUTCOME_TIMEOUT", failure.getCode());
    InOrder order = inOrder(transferOutcomeServiceMock, puMigrationServiceMock);
    order.verify(transferOutcomeServiceMock).getRequired(MIGRATION_ID, FILE_TYPE);
    order.verify(puMigrationServiceMock).getMigrationUpload(42L);
    if (status == UploadsStatusEnum.PROCESSING) {
      order.verify(puMigrationServiceMock).getMigrationUploadDetails(42L);
    }
    order.verify(transferOutcomeServiceMock).recordTransferOutcome(FILE_ID, 42L, upload, List.of());
    order.verify(transferOutcomeServiceMock).markFailed(FILE_ID, 42L, "TRANSFER_OUTCOME_TIMEOUT", message);
  }

  private TransferOutcomeCollector collector(Clock clock) {
    return new TransferOutcomeCollector(transferOutcomeServiceMock, puMigrationServiceMock,
      new MigrationFileExecutionTransferOutcomeValidator(),
      new TransferOutcomePollingProperties(Duration.ofSeconds(1), Duration.ofHours(12)), clock);
  }

  private void expectFailure(String code, String message) {
    when(transferOutcomeServiceMock.markFailed(FILE_ID, 42L, code, message)).thenReturn(file(FAILED));
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
