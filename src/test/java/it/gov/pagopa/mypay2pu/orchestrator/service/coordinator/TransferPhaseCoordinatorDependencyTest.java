package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferPhaseException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFacadeService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationPlanExecutorService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferPhaseCoordinatorDependencyTest {

  private static final UUID MIGRATION_ID = UUID.randomUUID();

  @Mock
  private MigrationFacadeService migrationFacadeServiceMock;
  @Mock
  private MigrationFileExecutionRepository migrationFileExecutionRepositoryMock;

  private TransferPhaseCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator = new TransferPhaseCoordinator(
      migrationFacadeServiceMock,
      migrationFileExecutionRepositoryMock,
      new MigrationPlanExecutorService(),
      1,
      2
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      migrationFacadeServiceMock,
      migrationFileExecutionRepositoryMock
    );
  }

  @Test
  void givenSelectedChildHasCompletedUnselectedParentWhenStartThenTransferAndComplete() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.COMPLETED
    )));

    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS);
    coordinator.start(execution).join();

    assertEquals(MigrationExecutionStatus.COMPLETED, execution.getStatus());
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORGANIZATIONS))
    ));
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    verify(migrationFileExecutionRepositoryMock, times(2)).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
  }

  @Test
  void givenSelectedChildHasWarningUnselectedParentWhenStartThenTransferAndWarnMigration() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.WARNING
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.COMPLETED
    )));

    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS);
    coordinator.start(execution).join();

    assertEquals(MigrationExecutionStatus.WARNING, execution.getStatus());
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORGANIZATIONS))
    ));
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    verify(migrationFileExecutionRepositoryMock, times(2)).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
  }

  @Test
  void givenSelectedChildHasParentWaitingOnPuWhenStartThenWaitForParentBeforeTransfer() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.WAITING_PU
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.COMPLETED
    )));

    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS);
    coordinator.start(execution).join();

    assertEquals(MigrationExecutionStatus.COMPLETED, execution.getStatus());
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORGANIZATIONS))
    ));
    verify(migrationFileExecutionRepositoryMock, times(2)).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    verify(migrationFileExecutionRepositoryMock, times(2)).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
  }

  @Test
  void givenSelectedChildHasFailedUnselectedParentWhenStartThenSkipDescendantAndFailMigration() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.FAILED
    )));

    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS);
    coordinator.start(execution).join();

    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    assertEquals("TRANSFER_FAILED", execution.getErrorCode());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
  }

  @Test
  void givenSelectedChildHasUnfinishedUnselectedParentWhenStartThenRejectTransfer() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.EXPORTED
    )));

    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS);
    TransferPhaseException exception = assertThrows(
      TransferPhaseException.class,
      () -> coordinator.start(execution)
    );

    assertEquals("TRANSFER_DEPENDENCY_NOT_READY", exception.getCode());
    assertEquals(
      "Parent file type DEBT_POSITIONS_TYPE for migration %s has not completed on PU (status=EXPORTED)"
        .formatted(MIGRATION_ID),
      exception.getMessage()
    );
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
  }

  private MigrationExecution execution(MigrationFileType... fileTypes) {
    return MigrationExecution.builder()
      .id(MIGRATION_ID)
      .action(MigrationAction.TRANSFER)
      .status(MigrationExecutionStatus.RUNNING)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipacodes(List.of("80000000000"))
      .fileTypes(List.of(fileTypes))
      .build();
  }

  private MigrationFileExecution fileExecution(
    MigrationFileType fileType,
    MigrationFileExecutionStatus status
  ) {
    return MigrationFileExecution.builder()
      .migrationId(MIGRATION_ID)
      .fileType(fileType)
      .status(status)
      .build();
  }
}
