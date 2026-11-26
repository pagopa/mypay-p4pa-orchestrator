package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationActionExecutorTest {

  @Mock
  private MigrationFacadeService migrationFacadeServiceMock;
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;

  private MigrationActionExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new MigrationActionExecutor(migrationFacadeServiceMock, migrationExecutionRepositoryMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationFacadeServiceMock, migrationExecutionRepositoryMock);
  }

  @ParameterizedTest
  @EnumSource(MigrationAction.class)
  void givenSuccessfulActionWhenExecuteThenPersistsCompletedMigration(MigrationAction action) {
    MigrationRequestDto request = new MigrationRequestDto().action(action);
    MigrationExecution execution = execution(action);

    assertTrue(executor.execute(request, execution).isDone());

    assertEquals(MigrationExecutionStatus.COMPLETED, execution.getStatus());
    assertEquals(1, execution.getFileTypeCountCompleted());
    assertNull(execution.getErrorCode());
    assertNull(execution.getErrorMsg());
    assertEquals(execution.getId(), request.getMigrationId());
    assertEquals(action == MigrationAction.TRANSFER ? null : execution.getDateTo(),
      execution.getLastSuccessfulExtractionAt());
    verify(migrationFacadeServiceMock).execute(same(request));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  @ParameterizedTest
  @EnumSource(MigrationAction.class)
  void givenActionFailureWhenExecuteThenPersistsFailureAndPropagates(MigrationAction action) {
    MigrationRequestDto request = new MigrationRequestDto().action(action);
    MigrationExecution execution = execution(action);
    IllegalStateException failure = new IllegalStateException("operation failed");
    doThrow(failure).when(migrationFacadeServiceMock).execute(same(request));

    assertSame(failure, assertThrows(IllegalStateException.class, () -> executor.execute(request, execution)));

    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    assertEquals(action + "_FAILED", execution.getErrorCode());
    assertEquals("operation failed", execution.getErrorMsg());
    verify(migrationFacadeServiceMock).execute(same(request));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  @ParameterizedTest
  @EnumSource(value = MigrationAction.class, names = {"EXPORT", "COMPLETE"})
  void givenSuccessfulDeltaWhenExecuteThenPersistsExclusiveEndAsCheckpoint(MigrationAction action) {
    MigrationRequestDto request = new MigrationRequestDto().action(action);
    MigrationExecution execution = execution(action);
    execution.setOrchestrationMode(OrchestrationMode.DELTA);

    executor.execute(request, execution);

    assertEquals(execution.getDateTo(), execution.getLastSuccessfulExtractionAt());
    verify(migrationFacadeServiceMock).execute(same(request));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  @Test
  void givenFailedBoundedExportWhenExecuteThenDoesNotAdvanceCheckpoint() {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.EXPORT);
    MigrationExecution execution = execution(MigrationAction.EXPORT);
    IllegalStateException failure = new IllegalStateException("failed");
    doThrow(failure).when(migrationFacadeServiceMock).execute(same(request));

    assertThrows(IllegalStateException.class, () -> executor.execute(request, execution));

    assertNull(execution.getLastSuccessfulExtractionAt());
    verify(migrationFacadeServiceMock).execute(same(request));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  @Test
  void givenTerminalSaveFailureWhenExecuteThenRestoresCheckpointBeforeSavingFailure() {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.EXPORT);
    MigrationExecution execution = execution(MigrationAction.EXPORT);
    LocalDateTime previousCheckpoint = execution.getDateTo().minusDays(1);
    execution.setLastSuccessfulExtractionAt(previousCheckpoint);
    IllegalStateException failure = new IllegalStateException("save failed");
    when(migrationExecutionRepositoryMock.save(same(execution))).thenThrow(failure).thenReturn(execution);

    assertThrows(IllegalStateException.class, () -> executor.execute(request, execution));

    assertEquals(previousCheckpoint, execution.getLastSuccessfulExtractionAt());
    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    verify(migrationFacadeServiceMock).execute(same(request));
    verify(migrationExecutionRepositoryMock, times(2)).save(same(execution));
  }

  @ParameterizedTest
  @EnumSource(value = OrchestrationMode.class, names = {"FULL", "DELTA"})
  void givenTransferWithCheckpointWhenExecuteThenPreservesCheckpoint(OrchestrationMode mode) {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.TRANSFER);
    MigrationExecution execution = execution(MigrationAction.TRANSFER);
    execution.setOrchestrationMode(mode);
    LocalDateTime previousCheckpoint = execution.getDateTo().minusDays(1);
    execution.setLastSuccessfulExtractionAt(previousCheckpoint);

    executor.execute(request, execution);

    assertEquals(previousCheckpoint, execution.getLastSuccessfulExtractionAt());
    verify(migrationFacadeServiceMock).execute(request);
    verify(migrationExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenRejectsOnlyCompleteWhenExecuteThenDoesNotAcquireCheckpoint() {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.COMPLETE);
    MigrationExecution execution = execution(MigrationAction.COMPLETE);
    execution.setOrchestrationMode(OrchestrationMode.REJECTS_ONLY);
    execution.setDateTo(null);

    executor.execute(request, execution);

    assertEquals(MigrationExecutionStatus.COMPLETED, execution.getStatus());
    assertNull(execution.getLastSuccessfulExtractionAt());
    verify(migrationFacadeServiceMock).execute(request);
    verify(migrationExecutionRepositoryMock).save(execution);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void givenFailurePersistenceErrorWhenExecuteThenPreservesOriginalFailure(String message) {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.EXPORT);
    MigrationExecution execution = execution(MigrationAction.EXPORT);
    IllegalStateException failure = new IllegalStateException(message);
    IllegalArgumentException persistenceFailure = new IllegalArgumentException("database unavailable");
    doThrow(failure).when(migrationFacadeServiceMock).execute(same(request));
    when(migrationExecutionRepositoryMock.save(same(execution))).thenThrow(persistenceFailure);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> executor.execute(request, execution));

    assertSame(failure, thrown);
    assertEquals(List.of(persistenceFailure), List.of(thrown.getSuppressed()));
    assertEquals("IllegalStateException", execution.getErrorMsg());
    verify(migrationFacadeServiceMock).execute(same(request));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  @Test
  void givenBusinessFailureWhenExecuteThenPersistsSpecificFailureCode() {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.TRANSFER);
    MigrationExecution execution = execution(MigrationAction.TRANSFER);
    ConflictException failure = new ConflictException("TRANSFER_INVALID_UPLOAD_RESPONSE", "invalid upload");
    doThrow(failure).when(migrationFacadeServiceMock).execute(same(request));

    assertSame(failure, assertThrows(
      ConflictException.class,
      () -> executor.execute(request, execution)
    ));

    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    assertEquals("TRANSFER_INVALID_UPLOAD_RESPONSE", execution.getErrorCode());
    assertEquals("invalid upload", execution.getErrorMsg());
    verify(migrationFacadeServiceMock).execute(same(request));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  private MigrationExecution execution(MigrationAction action) {
    return MigrationExecution.builder()
      .id(UUID.randomUUID())
      .action(action)
      .orchestrationMode(OrchestrationMode.FULL)
      .dateTo(LocalDateTime.of(2026, Month.JANUARY, 2, 12, 30, 45, 123456000))
      .status(MigrationExecutionStatus.RUNNING)
      .fileTypeCountCompleted(7)
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .errorCode("previous error")
      .errorMsg("previous message")
      .build();
  }
}
