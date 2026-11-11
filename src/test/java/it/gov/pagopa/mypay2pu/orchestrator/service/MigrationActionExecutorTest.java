package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferPhaseException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.TransferPhaseCoordinator;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationActionExecutorTest {

  @Mock
  private MigrationFacadeService migrationFacadeServiceMock;
  @Mock
  private TransferPhaseCoordinator transferPhaseCoordinatorMock;
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;

  private MigrationActionExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new MigrationActionExecutor(
      migrationFacadeServiceMock,
      transferPhaseCoordinatorMock,
      migrationExecutionRepositoryMock
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      migrationFacadeServiceMock,
      transferPhaseCoordinatorMock,
      migrationExecutionRepositoryMock
    );
  }

  @ParameterizedTest
  @EnumSource(MigrationAction.class)
  void givenSuccessfulActionWhenExecuteThenPersistsCompletedMigration(MigrationAction action) {
    MigrationRequestDto request = new MigrationRequestDto().action(action);
    MigrationExecution execution = execution(action);
    if (action == MigrationAction.TRANSFER) {
      doAnswer(invocation -> {
        MigrationExecution transferExecution = invocation.getArgument(0);
        transferExecution.setStatus(MigrationExecutionStatus.COMPLETED);
        return null;
      }).when(transferPhaseCoordinatorMock).execute(same(execution));
    }

    assertTrue(executor.execute(request, execution).isDone());

    assertEquals(MigrationExecutionStatus.COMPLETED, execution.getStatus());
    assertEquals(1, execution.getFileTypeCountCompleted());
    assertNull(execution.getErrorCode());
    assertNull(execution.getErrorMsg());
    assertEquals(execution.getId(), request.getMigrationId());
    if (action == MigrationAction.TRANSFER) {
      verify(transferPhaseCoordinatorMock).execute(same(execution));
    } else {
      verify(migrationFacadeServiceMock).execute(same(request));
    }
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  @ParameterizedTest
  @EnumSource(MigrationAction.class)
  void givenActionFailureWhenExecuteThenPersistsFailureAndPropagates(MigrationAction action) {
    MigrationRequestDto request = new MigrationRequestDto().action(action);
    MigrationExecution execution = execution(action);
    IllegalStateException failure = new IllegalStateException("operation failed");
    if (action == MigrationAction.TRANSFER) {
      doThrow(failure).when(transferPhaseCoordinatorMock).execute(same(execution));
    } else {
      doThrow(failure).when(migrationFacadeServiceMock).execute(same(request));
    }

    assertSame(failure, assertThrows(IllegalStateException.class, () -> executor.execute(request, execution)));

    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    assertEquals(action + "_FAILED", execution.getErrorCode());
    assertEquals("operation failed", execution.getErrorMsg());
    if (action == MigrationAction.TRANSFER) {
      verify(transferPhaseCoordinatorMock).execute(same(execution));
    } else {
      verify(migrationFacadeServiceMock).execute(same(request));
    }
    verify(migrationExecutionRepositoryMock).save(same(execution));
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
  void givenTransferPhaseFailureWhenExecuteThenPersistsSpecificFailureCode() {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.TRANSFER);
    MigrationExecution execution = execution(MigrationAction.TRANSFER);
    TransferPhaseException failure = new TransferPhaseException("TRANSFER_PU_TIMEOUT", "workflow timed out");
    doThrow(failure).when(transferPhaseCoordinatorMock).execute(same(execution));

    assertSame(failure, assertThrows(
      TransferPhaseException.class,
      () -> executor.execute(request, execution)
    ));

    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    assertEquals("TRANSFER_PU_TIMEOUT", execution.getErrorCode());
    assertEquals("workflow timed out", execution.getErrorMsg());
    verify(transferPhaseCoordinatorMock).execute(same(execution));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  @Test
  void givenBusinessFailureWhenExecuteThenPreservesSpecificFailureCode() {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.TRANSFER);
    MigrationExecution execution = execution(MigrationAction.TRANSFER);
    ConflictException failure = new ConflictException("PU_VALIDATION_ERROR", "PU rejected the archive");
    doThrow(failure).when(transferPhaseCoordinatorMock).execute(same(execution));

    assertSame(failure, assertThrows(
      ConflictException.class,
      () -> executor.execute(request, execution)
    ));

    assertEquals("PU_VALIDATION_ERROR", execution.getErrorCode());
    assertEquals("PU rejected the archive", execution.getErrorMsg());
    verify(transferPhaseCoordinatorMock).execute(same(execution));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  @Test
  void givenTransferWarningWhenExecuteThenPersistsWarningWithoutOverwritingIt() {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.TRANSFER);
    MigrationExecution execution = execution(MigrationAction.TRANSFER);
    doAnswer(invocation -> {
      MigrationExecution transferExecution = invocation.getArgument(0);
      transferExecution.setStatus(MigrationExecutionStatus.WARNING);
      return null;
    }).when(transferPhaseCoordinatorMock).execute(same(execution));

    executor.execute(request, execution);

    assertEquals(MigrationExecutionStatus.WARNING, execution.getStatus());
    assertEquals(1, execution.getFileTypeCountCompleted());
    assertNull(execution.getErrorCode());
    assertNull(execution.getErrorMsg());
    verify(transferPhaseCoordinatorMock).execute(same(execution));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  @Test
  void givenFailedTransferOutcomeWhenExecuteThenPersistsFailureAndPreservesPreviousFileCount() {
    MigrationRequestDto request = new MigrationRequestDto().action(MigrationAction.TRANSFER);
    MigrationExecution execution = execution(MigrationAction.TRANSFER);
    doAnswer(invocation -> {
      MigrationExecution transferExecution = invocation.getArgument(0);
      transferExecution.setStatus(MigrationExecutionStatus.FAILED);
      transferExecution.setErrorCode("TRANSFER_FAILED");
      transferExecution.setErrorMsg("one file failed");
      return null;
    }).when(transferPhaseCoordinatorMock).execute(same(execution));

    executor.execute(request, execution);

    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    assertEquals(1, execution.getFileTypeCountCompleted());
    assertEquals("TRANSFER_FAILED", execution.getErrorCode());
    assertEquals("one file failed", execution.getErrorMsg());
    verify(transferPhaseCoordinatorMock).execute(same(execution));
    verify(migrationExecutionRepositoryMock).save(same(execution));
  }

  private MigrationExecution execution(MigrationAction action) {
    return MigrationExecution.builder()
        .id(UUID.randomUUID())
        .action(action)
        .status(MigrationExecutionStatus.RUNNING)
        .fileTypeCountCompleted(1)
        .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
        .errorCode("previous error")
        .errorMsg("previous message")
        .build();
  }
}
