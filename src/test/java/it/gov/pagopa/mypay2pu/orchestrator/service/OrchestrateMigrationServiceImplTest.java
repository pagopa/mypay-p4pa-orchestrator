package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.CompleteMigrationPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.ExportPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.TransferPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationExecutionMapper;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationResponseMapper;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.refEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestrateMigrationServiceImplTest {

  @Mock
  private ExportPhaseCoordinator exportPhaseCoordinatorMock;
  @Mock
  private TransferPhaseCoordinator transferPhaseCoordinatorMock;
  @Mock
  private CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinatorMock;
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;

  private OrchestrateMigrationServiceImpl service;
  private static final LocalDateTime CREATION_DATE = LocalDateTime.of(2026, Month.JANUARY, 1, 12, 30);

  @BeforeEach
  void setUp() {
    service = new OrchestrateMigrationServiceImpl(
      exportPhaseCoordinatorMock,
      transferPhaseCoordinatorMock,
      completeMigrationPhaseCoordinatorMock,
      new MigrationRequestValidator(migrationExecutionRepositoryMock),
      migrationExecutionRepositoryMock,
      new MigrationExecutionMapper(),
      new MigrationResponseMapper()
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      exportPhaseCoordinatorMock,
      transferPhaseCoordinatorMock,
      completeMigrationPhaseCoordinatorMock,
      migrationExecutionRepositoryMock
    );
  }

  @Test
  void exportDelegatesSavedExecutionAndReturnsInitialResponse() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.EXPORT);
    request.setOrchestrationMode(OrchestrationMode.DELTA);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    MigrationExecution expectedExecution = MigrationExecution.builder()
      .status(MigrationExecutionStatus.RUNNING)
      .fileTypeCountCompleted(1)
      .ipacodes(List.of("80000000000"))
      .orchestrationMode(OrchestrationMode.DELTA)
      .action(MigrationAction.EXPORT)
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .build();
    UUID migrationId = UUID.randomUUID();
    MigrationExecution savedExecution = MigrationExecution.builder()
      .id(migrationId)
      .status(MigrationExecutionStatus.RUNNING)
      .fileTypeCountCompleted(1)
      .ipacodes(List.of("80000000000"))
      .orchestrationMode(OrchestrationMode.DELTA)
      .action(MigrationAction.EXPORT)
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .creationDate(CREATION_DATE)
      .build();
    when(migrationExecutionRepositoryMock.save(refEq(expectedExecution, "creationDate"))).thenReturn(savedExecution);
    when(exportPhaseCoordinatorMock.start(savedExecution)).thenReturn(CompletableFuture.completedFuture(null));

    MigrationResponseDto response = service.startMigration(request);

    InOrder inOrder = inOrder(migrationExecutionRepositoryMock, exportPhaseCoordinatorMock);
    ArgumentCaptor<MigrationExecution> executionCaptor = ArgumentCaptor.forClass(MigrationExecution.class);
    inOrder.verify(migrationExecutionRepositoryMock).save(executionCaptor.capture());
    assertNotNull(executionCaptor.getValue().getCreationDate());
    inOrder.verify(exportPhaseCoordinatorMock).start(savedExecution);
    assertEquals(migrationId, response.getMigrationId());
    assertEquals(MigrationAction.EXPORT, response.getAction());
    assertEquals(MigrationExecutionStatus.RUNNING, response.getStatus());
    assertEquals(OrchestrationMode.DELTA, response.getOrchestrationMode());
  }

  @Test
  void rejectsOnlyExportLinksPreviousMigration() {
    UUID previousMigrationId = UUID.randomUUID();
    MigrationExecution previousExecution = MigrationExecution.builder()
      .id(previousMigrationId)
      .status(MigrationExecutionStatus.COMPLETED)
      .action(MigrationAction.COMPLETE)
      .build();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.EXPORT);
    request.setOrchestrationMode(OrchestrationMode.REJECTS_ONLY);
    request.setMigrationId(previousMigrationId);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    when(migrationExecutionRepositoryMock.findById(previousMigrationId)).thenReturn(Optional.of(previousExecution));
    when(migrationExecutionRepositoryMock.save(org.mockito.ArgumentMatchers.argThat(
      execution -> previousMigrationId.equals(execution.getParentExecutionId())
    ))).thenAnswer(invocation -> invocation.getArgument(0));
    when(exportPhaseCoordinatorMock.start(org.mockito.ArgumentMatchers.argThat(
      execution -> previousMigrationId.equals(execution.getParentExecutionId())
    ))).thenReturn(CompletableFuture.completedFuture(null));

    MigrationResponseDto response = service.startMigration(request);

    assertEquals(MigrationAction.EXPORT, response.getAction());
    verify(migrationExecutionRepositoryMock).findById(previousMigrationId);
    verify(migrationExecutionRepositoryMock).save(org.mockito.ArgumentMatchers.argThat(
      execution -> previousMigrationId.equals(execution.getParentExecutionId())
    ));
    verify(exportPhaseCoordinatorMock).start(org.mockito.ArgumentMatchers.argThat(
      execution -> previousMigrationId.equals(execution.getParentExecutionId())
    ));
  }

  @Test
  void transferDelegatesPersistedExecutionAndReturnsInitialResponse() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(migrationId);
    request.setOrchestrationMode(OrchestrationMode.DELTA);
    MigrationExecution execution = existingExecution(migrationId, MigrationExecutionStatus.COMPLETED);
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));
    when(migrationExecutionRepositoryMock.save(execution)).thenReturn(execution);
    when(transferPhaseCoordinatorMock.start(execution)).thenReturn(CompletableFuture.completedFuture(null));

    MigrationResponseDto response = service.startMigration(request);

    InOrder inOrder = inOrder(migrationExecutionRepositoryMock, transferPhaseCoordinatorMock);
    inOrder.verify(migrationExecutionRepositoryMock).findById(migrationId);
    inOrder.verify(migrationExecutionRepositoryMock).save(execution);
    inOrder.verify(transferPhaseCoordinatorMock).start(execution);
    assertEquals(MigrationExecutionStatus.RUNNING, execution.getStatus());
    assertEquals(migrationId, response.getMigrationId());
    assertEquals(MigrationAction.TRANSFER, response.getAction());
    assertEquals(MigrationExecutionStatus.RUNNING, response.getStatus());
    assertEquals(OrchestrationMode.DELTA, response.getOrchestrationMode());
    assertEquals(List.of(), response.getUploadIds());
    assertNotNull(response.getCreatedAt());
  }

  @Test
  void completeDelegatesSavedExecutionAndReturnsInitialResponse() {
    MigrationRequestDto request = completeRequest();
    MigrationExecution expectedExecution = expectedCompleteExecution();
    UUID migrationId = UUID.randomUUID();
    MigrationExecution savedExecution = expectedCompleteExecution();
    savedExecution.setId(migrationId);
    savedExecution.setCreationDate(CREATION_DATE);
    when(migrationExecutionRepositoryMock.save(refEq(expectedExecution, "creationDate"))).thenReturn(savedExecution);

    MigrationResponseDto response = service.startMigration(request);

    InOrder inOrder = inOrder(migrationExecutionRepositoryMock, completeMigrationPhaseCoordinatorMock);
    ArgumentCaptor<MigrationExecution> executionCaptor = ArgumentCaptor.forClass(MigrationExecution.class);
    inOrder.verify(migrationExecutionRepositoryMock).save(executionCaptor.capture());
    assertNotNull(executionCaptor.getValue().getCreationDate());
    inOrder.verify(completeMigrationPhaseCoordinatorMock).start(savedExecution);
    assertEquals(migrationId, response.getMigrationId());
    assertEquals(MigrationAction.COMPLETE, response.getAction());
    assertEquals(MigrationExecutionStatus.RUNNING, response.getStatus());
    assertEquals(OrchestrationMode.DELTA, response.getOrchestrationMode());
    assertEquals(List.of("80000000000"), response.getIpaCodes());
    assertNotNull(response.getCreatedAt());
  }

  @Test
  void missingActionIsRejected() {
    MigrationRequestDto request = new MigrationRequestDto();

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("action is required", exception.getMessage());
  }

  @Test
  void transferWithoutMigrationIdIsRejected() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setOrchestrationMode(OrchestrationMode.DELTA);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("migrationId is required for TRANSFER", exception.getMessage());
  }

  @Test
  void transferWithUnknownMigrationIdIsRejected() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(migrationId);
    request.setOrchestrationMode(OrchestrationMode.DELTA);
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.empty());

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("migrationId does not identify an existing migration", exception.getMessage());
    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  private MigrationRequestDto completeRequest() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.COMPLETE);
    request.setOrchestrationMode(OrchestrationMode.DELTA);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    return request;
  }

  private MigrationExecution expectedCompleteExecution() {
    return MigrationExecution.builder()
      .status(MigrationExecutionStatus.RUNNING)
      .fileTypeCountCompleted(1)
      .ipacodes(List.of("80000000000"))
      .orchestrationMode(OrchestrationMode.DELTA)
      .action(MigrationAction.COMPLETE)
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .build();
  }

  private MigrationExecution existingExecution(UUID migrationId, MigrationExecutionStatus status) {
    return MigrationExecution.builder()
      .id(migrationId)
      .status(status)
      .action(MigrationAction.EXPORT)
      .fileTypeCountCompleted(1)
      .ipacodes(List.of("80000000000"))
      .orchestrationMode(OrchestrationMode.DELTA)
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .creationDate(CREATION_DATE)
      .build();
  }
}
