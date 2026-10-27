package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferPhaseCoordinatorTest {

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
      3
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
  void givenParentExportedWhenStartThenChildTransfersAfterParentCompletes() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
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

    coordinator.start(execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS
    )).join();

    InOrder inOrder = inOrder(migrationFileExecutionRepositoryMock, migrationFacadeServiceMock);
    inOrder.verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    ArgumentCaptor<MigrationRequestDto> requestCaptor = ArgumentCaptor.forClass(MigrationRequestDto.class);
    inOrder.verify(migrationFacadeServiceMock).execute(requestCaptor.capture());
    assertTransferRequest(requestCaptor.getValue(), MigrationFileType.DEBT_POSITIONS_TYPE);
    inOrder.verify(migrationFileExecutionRepositoryMock, times(2)).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    inOrder.verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
    inOrder.verify(migrationFacadeServiceMock).execute(requestCaptor.capture());
    assertTransferRequest(requestCaptor.getValue(), MigrationFileType.ORGANIZATIONS);
    inOrder.verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
  }

  @Test
  void givenParentWarningWhenStartThenChildTransferContinues() {
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
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileExecutionStatus.WARNING
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileExecutionStatus.COMPLETED
    )));

    coordinator.start(execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.DEBT_POSITIONS
    )).join();

    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORGANIZATIONS))
    ));
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG))
    ));
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.DEBT_POSITIONS))
    ));
  }

  @Test
  void givenParentFailedWhenStartThenDependentRemainsPending() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.FAILED
    )));

    coordinator.start(execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS
    )).join();

    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
  }

  @Test
  void givenIndependentBranchWhenAnotherParentFailsThenIndependentBranchTransfers() {
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
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileExecutionStatus.FAILED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORG_SIL_SERVICES
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORG_SIL_SERVICES,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORG_SIL_SERVICES,
      MigrationFileExecutionStatus.COMPLETED
    )));

    coordinator.start(execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.ORG_SIL_SERVICES,
      MigrationFileType.DEBT_POSITIONS_PAID,
      MigrationFileType.ASSESSMENTS
    )).join();

    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORG_SIL_SERVICES))
    ));
  }

  @Test
  void givenParentNeverCompletesWhenStartThenStartFailsAfterPollingTimeout() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.WAITING_PU
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.WAITING_PU
    )));

    MigrationExecution migrationExecution = execution(MigrationFileType.DEBT_POSITIONS_TYPE);
    IllegalStateException exception = assertThrows(
      IllegalStateException.class,
      () -> coordinator.start(migrationExecution)
    );

    assertEquals(
      "Timed out waiting for PU completion of file type DEBT_POSITIONS_TYPE for migration %s after 3 attempts"
        .formatted(MIGRATION_ID),
      exception.getMessage()
    );
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.DEBT_POSITIONS_TYPE))
    ));
  }

  private MigrationExecution execution(MigrationFileType... fileTypes) {
    return MigrationExecution.builder()
      .id(MIGRATION_ID)
      .action(MigrationAction.TRANSFER)
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

  private void assertTransferRequest(MigrationRequestDto request, MigrationFileType fileType) {
    assertEquals(MigrationAction.TRANSFER, request.getAction());
    assertEquals(OrchestrationMode.FULL, request.getOrchestrationMode());
    assertEquals(MIGRATION_ID, request.getMigrationId());
    assertEquals(List.of("80000000000"), request.getIpaCodes());
    assertEquals(List.of(fileType), request.getFileTypesToInclude());
  }
}
