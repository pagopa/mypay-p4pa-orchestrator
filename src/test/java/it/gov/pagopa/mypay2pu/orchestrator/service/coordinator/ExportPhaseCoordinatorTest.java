package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFacadeService;
import java.time.LocalDate;
import org.mockito.ArgumentCaptor;

import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportPhaseCoordinatorTest {

  @Mock
  private MigrationFacadeService migrationFacadeServiceMock;
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;

  private ExportPhaseCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator = new ExportPhaseCoordinator(
      migrationFacadeServiceMock,
      migrationExecutionRepositoryMock
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationFacadeServiceMock, migrationExecutionRepositoryMock);
  }

  @Test
  void givenSuccessfulPlanWhenStartThenPersistsCompletedMigration() {
    MigrationExecution execution = execution();
    when(migrationExecutionRepositoryMock.save(execution)).thenReturn(execution);

    coordinator.start(execution);

    assertEquals(MigrationExecutionStatus.COMPLETED, execution.getStatus());
    assertEquals(2, execution.getFileTypeCountCompleted());
    assertNull(execution.getErrorCode());
    ArgumentCaptor<MigrationRequestDto> requestCaptor = ArgumentCaptor.forClass(MigrationRequestDto.class);
    verify(migrationFacadeServiceMock).execute(requestCaptor.capture());
    MigrationRequestDto request = requestCaptor.getValue();
    assertEquals(MigrationAction.EXPORT, request.getAction());
    assertEquals(OrchestrationMode.FULL, request.getOrchestrationMode());
    assertEquals(execution.getId(), request.getMigrationId());
    assertEquals(execution.getIpacodes(), request.getIpaCodes());
    assertEquals(execution.getFileTypes(), request.getFileTypesToInclude());
    assertNull(request.getPeriod());
    verify(migrationExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenExecutionPeriodWhenStartThenRequestContainsPeriod() {
    MigrationExecution execution = execution();
    execution.setDateFrom(LocalDate.of(2024, Month.JANUARY, 1));
    execution.setDateTo(LocalDate.of(2024, Month.DECEMBER, 31));
    when(migrationExecutionRepositoryMock.save(execution)).thenReturn(execution);

    coordinator.start(execution);

    ArgumentCaptor<MigrationRequestDto> requestCaptor = ArgumentCaptor.forClass(MigrationRequestDto.class);
    verify(migrationFacadeServiceMock).execute(requestCaptor.capture());
    assertEquals(
      new PeriodDto(LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
      requestCaptor.getValue().getPeriod()
    );
    verify(migrationExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenPlanFailureWhenStartThenPersistsFailureAndPropagates() {
    MigrationExecution execution = execution();
    IllegalStateException failure = new IllegalStateException("Extractor failed");
    when(migrationExecutionRepositoryMock.save(execution)).thenReturn(execution);
    doThrow(failure).when(migrationFacadeServiceMock).execute(any(MigrationRequestDto.class));

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> coordinator.start(execution));

    assertEquals(failure, thrown);
    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    assertEquals("EXPORT_FAILED", execution.getErrorCode());
    assertEquals("Extractor failed", execution.getErrorMsg());
    verify(migrationFacadeServiceMock).execute(any(MigrationRequestDto.class));
    verify(migrationExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenFailurePersistenceErrorWhenStartThenPreservesPlanFailure() {
    MigrationExecution execution = execution();
    IllegalStateException failure = new IllegalStateException("Extractor failed");
    IllegalArgumentException persistenceFailure = new IllegalArgumentException("Database unavailable");
    when(migrationExecutionRepositoryMock.save(execution)).thenThrow(persistenceFailure);
    doThrow(failure).when(migrationFacadeServiceMock).execute(any(MigrationRequestDto.class));

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> coordinator.start(execution));

    assertEquals(failure, thrown);
    assertEquals(1, thrown.getSuppressed().length);
    assertEquals(persistenceFailure, thrown.getSuppressed()[0]);
    assertEquals(MigrationExecutionStatus.FAILED, execution.getStatus());
    verify(migrationFacadeServiceMock).execute(any(MigrationRequestDto.class));
    verify(migrationExecutionRepositoryMock).save(execution);
  }

  private MigrationExecution execution() {
    return MigrationExecution.builder()
      .id(UUID.randomUUID())
      .status(MigrationExecutionStatus.RUNNING)
      .fileTypeCountCompleted(0)
      .ipacodes(List.of("IPA1"))
      .orchestrationMode(OrchestrationMode.FULL)
      .action(MigrationAction.EXPORT)
      .fileTypes(List.of(MigrationFileType.DEBT_POSITIONS_TYPE, MigrationFileType.ORGANIZATIONS))
      .build();
  }
}
