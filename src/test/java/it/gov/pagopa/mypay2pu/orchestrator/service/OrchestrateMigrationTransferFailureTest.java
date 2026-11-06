package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferPhaseException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.CompleteMigrationPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.ExportPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.TransferPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationExecutionMapper;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationResponseMapper;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestrateMigrationTransferFailureTest {

  @Mock
  private ExportPhaseCoordinator exportPhaseCoordinatorMock;
  @Mock
  private TransferPhaseCoordinator transferPhaseCoordinatorMock;
  @Mock
  private CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinatorMock;
  @Mock
  private MigrationRequestValidator migrationRequestValidatorMock;
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;
  @Mock
  private MigrationExecutionMapper migrationExecutionMapperMock;
  @Mock
  private MigrationResponseMapper migrationResponseMapperMock;

  private OrchestrateMigrationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new OrchestrateMigrationServiceImpl(
      exportPhaseCoordinatorMock,
      transferPhaseCoordinatorMock,
      completeMigrationPhaseCoordinatorMock,
      migrationRequestValidatorMock,
      migrationExecutionRepositoryMock,
      migrationExecutionMapperMock,
      migrationResponseMapperMock
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      exportPhaseCoordinatorMock,
      transferPhaseCoordinatorMock,
      completeMigrationPhaseCoordinatorMock,
      migrationRequestValidatorMock,
      migrationExecutionRepositoryMock,
      migrationExecutionMapperMock,
      migrationResponseMapperMock
    );
  }

  @Test
  void givenTransferCoordinatorFailsWhenStartMigrationThenPersistFailedStatusAndError() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(migrationId);
    request.setOrchestrationMode(OrchestrationMode.FULL);
    MigrationExecution execution = MigrationExecution.builder()
      .id(migrationId)
      .status(MigrationExecutionStatus.COMPLETED)
      .action(MigrationAction.EXPORT)
      .fileTypeCountCompleted(1)
      .ipacodes(List.of("80000000000"))
      .orchestrationMode(OrchestrationMode.FULL)
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .build();
    TransferPhaseException transferFailure = new TransferPhaseException(
      "TRANSFER_PU_TIMEOUT",
      "Timed out waiting for PU completion"
    );
    MigrationResponseDto initialResponse = new MigrationResponseDto();
    when(migrationRequestValidatorMock.requireTransferExecution(request)).thenReturn(execution);
    when(migrationExecutionRepositoryMock.save(execution)).thenReturn(execution);
    when(migrationResponseMapperMock.toMigrationResponseDto(execution)).thenReturn(initialResponse);
    when(transferPhaseCoordinatorMock.start(execution)).thenReturn(CompletableFuture.failedFuture(transferFailure));

    MigrationResponseDto response = service.startMigration(request);

    assertSame(initialResponse, response);
    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    assertEquals("TRANSFER_PU_TIMEOUT", execution.getErrorCode());
    assertEquals("Timed out waiting for PU completion", execution.getErrorMsg());
    verify(migrationRequestValidatorMock).validate(request);
    verify(migrationRequestValidatorMock).requireTransferExecution(request);
    verify(migrationExecutionRepositoryMock, times(2)).save(execution);
    verify(transferPhaseCoordinatorMock).start(execution);
    verify(migrationResponseMapperMock).toMigrationResponseDto(execution);
  }
}
