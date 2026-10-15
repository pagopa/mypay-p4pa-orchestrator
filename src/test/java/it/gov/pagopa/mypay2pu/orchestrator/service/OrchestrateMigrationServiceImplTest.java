package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.CompleteMigrationPhaseCoordinator;
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
  private TransferPhaseCoordinator transferPhaseCoordinatorMock;
  @Mock
  private CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinatorMock;
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;

  private OrchestrateMigrationServiceImpl service;
  private static final String BROKER_IPA_CODE = "IPA_DEFAULT";
  private static final LocalDateTime CREATION_DATE = LocalDateTime.of(2026, Month.JANUARY, 1, 12, 30);

  @BeforeEach
  void setUp() {
    service = new OrchestrateMigrationServiceImpl(
      transferPhaseCoordinatorMock,
      completeMigrationPhaseCoordinatorMock,
      new MigrationRequestValidator(migrationExecutionRepositoryMock),
      migrationExecutionRepositoryMock,
      new MigrationExecutionMapper(BROKER_IPA_CODE),
      new MigrationResponseMapper()
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      transferPhaseCoordinatorMock,
      completeMigrationPhaseCoordinatorMock,
      migrationExecutionRepositoryMock
    );
  }

  @Test
  void transferDelegatesPersistedExecutionAndReturnsInitialResponse() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(migrationId);
    request.setOrchestationMode(CycleMode.DELTA);
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
    assertEquals(CycleMode.DELTA, response.getOrchestationMode());
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
    assertEquals(CycleMode.DELTA, response.getOrchestationMode());
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
    request.setOrchestationMode(CycleMode.DELTA);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("migrationId is required for TRANSFER", exception.getMessage());
  }

  @Test
  void transferWithUnknownMigrationIdIsRejected() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(migrationId);
    request.setOrchestationMode(CycleMode.DELTA);
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.empty());

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("migrationId does not identify an existing migration", exception.getMessage());
    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  @Test
  void exportRemainsExplicitlyUnsupported() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.EXPORT);
    request.setOrchestationMode(CycleMode.DELTA);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));

    assertThrows(UnsupportedOperationException.class, () -> service.startMigration(request));
  }

  private MigrationRequestDto completeRequest() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.COMPLETE);
    request.setOrchestationMode(CycleMode.DELTA);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    return request;
  }

  private MigrationExecution expectedCompleteExecution() {
    return MigrationExecution.builder()
      .brokerIpaCode(BROKER_IPA_CODE)
      .status(MigrationExecutionStatus.RUNNING)
      .cycleNumber(1)
      .ipacodes(List.of("80000000000"))
      .cycleMode(CycleMode.DELTA)
      .action(MigrationAction.COMPLETE)
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS.name()))
      .build();
  }

  private MigrationExecution existingExecution(UUID migrationId, MigrationExecutionStatus status) {
    return MigrationExecution.builder()
      .id(migrationId)
      .brokerIpaCode(BROKER_IPA_CODE)
      .status(status)
      .cycleNumber(1)
      .ipacodes(List.of("80000000000"))
      .cycleMode(CycleMode.DELTA)
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS.name()))
      .creationDate(CREATION_DATE)
      .build();
  }
}
