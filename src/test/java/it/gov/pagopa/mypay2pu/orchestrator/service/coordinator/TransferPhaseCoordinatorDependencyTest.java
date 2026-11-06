package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void givenSelectedChildWhenStartThenSkipUnselectedAncestorsAndTransferChild() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
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
      MigrationFileType.ORGANIZATIONS
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

  private MigrationFileExecution fileExecution(MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder()
      .migrationId(MIGRATION_ID)
      .fileType(MigrationFileType.ORGANIZATIONS)
      .status(status)
      .build();
  }
}
