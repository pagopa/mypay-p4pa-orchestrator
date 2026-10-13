package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.CompleteMigrationPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.TransferPhaseCoordinator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestrateMigrationServiceImplTest {

  @Mock
  private TransferPhaseCoordinator transferPhaseCoordinatorMock;
  @Mock
  private CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinatorMock;

  private OrchestrateMigrationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new OrchestrateMigrationServiceImpl(
      transferPhaseCoordinatorMock,
      completeMigrationPhaseCoordinatorMock
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(transferPhaseCoordinatorMock, completeMigrationPhaseCoordinatorMock);
  }

  @Test
  void transferDelegatesMigrationIdAndReturnsInitialResponse() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.TRANSFER);
    request.setMigrationId(migrationId);
    request.setCycleMode(CycleMode.DELTA);
    when(transferPhaseCoordinatorMock.start(migrationId)).thenReturn(CompletableFuture.completedFuture(null));

    MigrationResponseDto response = service.startMigration(request);

    verify(transferPhaseCoordinatorMock).start(migrationId);
    assertEquals(migrationId, response.getMigrationId());
    assertEquals(MigrationPhase.TRANSFER, response.getPhase());
    assertEquals(MigrationExecutionStatus.RUNNING, response.getStatus());
    assertEquals(CycleMode.DELTA, response.getCycleMode());
    assertEquals(List.of(), response.getUploadIds());
    assertNotNull(response.getCreatedAt());
  }

  @Test
  void completeDelegatesGeneratedMigrationIdAndReturnsInitialResponse() {
    MigrationRequestDto request = completeRequest();

    MigrationResponseDto response = service.startMigration(request);

    ArgumentCaptor<UUID> migrationIdCaptor = ArgumentCaptor.forClass(UUID.class);
    verify(completeMigrationPhaseCoordinatorMock).start(migrationIdCaptor.capture());
    assertEquals(migrationIdCaptor.getValue(), response.getMigrationId());
    assertEquals(MigrationPhase.COMPLETE, response.getPhase());
    assertEquals(MigrationExecutionStatus.RUNNING, response.getStatus());
    assertEquals(CycleMode.DELTA, response.getCycleMode());
    assertEquals(List.of("80000000000"), response.getIpaCodes());
    assertNotNull(response.getCreatedAt());
  }

  @Test
  void missingPhaseIsRejected() {
    MigrationRequestDto request = new MigrationRequestDto();

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("phase is required", exception.getMessage());
  }

  @Test
  void transferWithoutMigrationIdIsRejected() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.TRANSFER);
    request.setCycleMode(CycleMode.DELTA);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("migrationId is required for TRANSFER", exception.getMessage());
  }

  @Test
  void exportRemainsExplicitlyUnsupported() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.EXPORT);

    assertThrows(UnsupportedOperationException.class, () -> service.startMigration(request));
  }

  private MigrationRequestDto completeRequest() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.COMPLETE);
    request.setCycleMode(CycleMode.DELTA);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    return request;
  }
}
