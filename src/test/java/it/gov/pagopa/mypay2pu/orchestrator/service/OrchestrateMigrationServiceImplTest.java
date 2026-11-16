package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationExecutionMapper;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationResponseMapper;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestrateMigrationServiceImplTest {

  @Mock
  private MigrationActionExecutor migrationActionExecutorMock;
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;
  @Mock
  private MigrationFileExecutionRepository migrationFileExecutionRepositoryMock;

  private OrchestrateMigrationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new OrchestrateMigrationServiceImpl(
      migrationActionExecutorMock,
      new MigrationRequestValidator(migrationExecutionRepositoryMock, migrationFileExecutionRepositoryMock),
      migrationExecutionRepositoryMock,
      new MigrationExecutionMapper(),
      new MigrationResponseMapper(),
      new ExtractionWindowResolver(new MigrationRequestValidator(
        migrationExecutionRepositoryMock, migrationFileExecutionRepositoryMock), migrationExecutionRepositoryMock)
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationActionExecutorMock, migrationExecutionRepositoryMock, migrationFileExecutionRepositoryMock);
  }

  @ParameterizedTest
  @EnumSource(MigrationAction.class)
  void givenValidActionWhenStartMigrationThenDispatchesRequestAndSavedExecution(MigrationAction action) {
    MigrationRequestDto request = request(action);
    MigrationExecution saved = execution(UUID.randomUUID());
    saved.setAction(action);
    saved.setStatus(MigrationExecutionStatus.RUNNING);
    if (action == MigrationAction.TRANSFER) {
      MigrationExecution existing = execution(request.getMigrationId());
      when(migrationExecutionRepositoryMock.findById(request.getMigrationId())).thenReturn(Optional.of(existing));
    }
    when(migrationExecutionRepositoryMock.save(isA(MigrationExecution.class))).thenReturn(saved);
    when(migrationActionExecutorMock.execute(same(request), same(saved)))
      .thenReturn(CompletableFuture.completedFuture(null));

    MigrationResponseDto response = service.startMigration(request);

    assertEquals(saved.getId(), response.getMigrationId());
    assertEquals(MigrationExecutionStatus.RUNNING, response.getStatus());
    assertEquals(action, response.getAction());
    InOrder calls = inOrder(migrationExecutionRepositoryMock, migrationActionExecutorMock);
    if (action == MigrationAction.TRANSFER) {
      calls.verify(migrationExecutionRepositoryMock).findById(request.getMigrationId());
    }
    ArgumentCaptor<MigrationExecution> modelCaptor = ArgumentCaptor.forClass(MigrationExecution.class);
    calls.verify(migrationExecutionRepositoryMock).save(modelCaptor.capture());
    assertEquals(action, modelCaptor.getValue().getAction());
    assertEquals(MigrationExecutionStatus.RUNNING, modelCaptor.getValue().getStatus());
    assertNotNull(modelCaptor.getValue().getCreationDate());
    calls.verify(migrationActionExecutorMock).execute(same(request), same(saved));
  }

  @Test
  void givenRejectsOnlyCompleteWhenStartMigrationThenPreservesParentReference() {
    MigrationRequestDto request = request(MigrationAction.COMPLETE);
    request.setOrchestrationMode(OrchestrationMode.REJECTS_ONLY);
    UUID parentId = UUID.randomUUID();
    request.setMigrationId(parentId);
    when(migrationExecutionRepositoryMock.findById(parentId)).thenReturn(Optional.of(execution(parentId)));
    when(migrationExecutionRepositoryMock.save(isA(MigrationExecution.class))).thenAnswer(invocation -> {
      MigrationExecution saved = invocation.getArgument(0);
      saved.setId(UUID.randomUUID());
      return saved;
    });
    when(migrationActionExecutorMock.execute(same(request), isA(MigrationExecution.class)))
      .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("action failed")));

    MigrationResponseDto response = service.startMigration(request);

    ArgumentCaptor<MigrationExecution> captor = ArgumentCaptor.forClass(MigrationExecution.class);
    verify(migrationActionExecutorMock).execute(same(request), captor.capture());
    assertEquals(parentId, captor.getValue().getParentExecutionId());
    assertEquals(response.getMigrationId(), captor.getValue().getId());
    assertEquals(parentId, request.getMigrationId());
    verify(migrationExecutionRepositoryMock).findById(parentId);
    verify(migrationExecutionRepositoryMock).save(same(captor.getValue()));
  }

  @Test
  void givenMissingActionWhenStartMigrationThenRejectsRequest() {
    MigrationRequestDto request = new MigrationRequestDto();
    InvalidValueException failure = assertThrows(
      InvalidValueException.class, () -> service.startMigration(request)
    );
    assertEquals("action is required", failure.getMessage());
  }

  @Test
  void givenPreviousSuccessfulExtractionWhenStartMigrationThenUsesItsCheckpointAndKeepsSinceDateUnset() {
    UUID parentId = UUID.randomUUID();
    MigrationRequestDto request = request(MigrationAction.EXPORT)
      .orchestrationMode(OrchestrationMode.DELTA)
      .migrationId(parentId)
      .fileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS))
      .period(new PeriodDto(null, LocalDate.of(2026, Month.JANUARY, 3)));
    MigrationExecution previous = execution(parentId);
    previous.setFileTypes(List.of(MigrationFileType.DEBT_POSITIONS));
    previous.setDateTo(LocalDate.of(2026, Month.JANUARY, 2));
    previous.setLastSuccessfulExtractionAt(previous.getDateTo().atStartOfDay());
    MigrationFileExecution completed = MigrationFileExecution.builder()
      .migrationId(parentId).fileType(MigrationFileType.DEBT_POSITIONS)
      .status(MigrationFileExecutionStatus.EXPORTED).build();
    when(migrationExecutionRepositoryMock.findById(parentId)).thenReturn(Optional.of(previous));
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdOrderByCreationDateAscIdAsc(parentId))
      .thenReturn(List.of(completed));
    when(migrationExecutionRepositoryMock.save(isA(MigrationExecution.class))).thenAnswer(invocation -> {
      MigrationExecution saved = invocation.getArgument(0);
      saved.setId(UUID.randomUUID());
      return saved;
    });
    when(migrationActionExecutorMock.execute(same(request), isA(MigrationExecution.class)))
      .thenReturn(CompletableFuture.completedFuture(null));

    service.startMigration(request);

    ArgumentCaptor<MigrationExecution> executionCaptor = ArgumentCaptor.forClass(MigrationExecution.class);
    verify(migrationExecutionRepositoryMock).findById(parentId);
    verify(migrationFileExecutionRepositoryMock).findAllByMigrationIdOrderByCreationDateAscIdAsc(parentId);
    verify(migrationExecutionRepositoryMock).save(executionCaptor.capture());
    verify(migrationActionExecutorMock).execute(same(request), same(executionCaptor.getValue()));
    assertEquals(parentId, executionCaptor.getValue().getParentExecutionId());
    assertNull(executionCaptor.getValue().getSinceDate());
    assertEquals(previous.getLastSuccessfulExtractionAt(), executionCaptor.getValue().getDateFrom().atStartOfDay());
    assertEquals(LocalDate.of(2026, Month.JANUARY, 3), executionCaptor.getValue().getDateTo());
  }

  @Test
  void givenDeltaWithExcludedFileTypesWhenStartMigrationThenRejectsBeforeSaving() {
    MigrationRequestDto request = request(MigrationAction.EXPORT)
      .orchestrationMode(OrchestrationMode.DELTA)
      .migrationId(UUID.randomUUID())
      .fileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS, MigrationFileType.DEBT_POSITIONS));

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("fileTypesToInclude contains types not supported for DELTA", failure.getMessage());
  }

  @Test
  void givenUnknownDeltaParentWhenStartMigrationThenRejectsBeforeSaving() {
    UUID parentId = UUID.randomUUID();
    MigrationRequestDto request = request(MigrationAction.EXPORT)
      .orchestrationMode(OrchestrationMode.DELTA)
      .migrationId(parentId)
      .fileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS));
    when(migrationExecutionRepositoryMock.findById(parentId)).thenReturn(Optional.empty());

    assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    verify(migrationExecutionRepositoryMock).findById(parentId);
  }

  @Test
  void givenNonMidnightCheckpointWhenStartMigrationThenRejectsWithoutTruncating() {
    UUID parentId = UUID.randomUUID();
    MigrationRequestDto request = request(MigrationAction.EXPORT)
      .orchestrationMode(OrchestrationMode.DELTA)
      .migrationId(parentId)
      .fileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS));
    MigrationExecution previous = execution(parentId);
    previous.setFileTypes(request.getFileTypesToInclude());
    previous.setDateTo(LocalDate.of(2026, Month.JANUARY, 2));
    previous.setLastSuccessfulExtractionAt(previous.getDateTo().atTime(12, 30, 0, 123000000));
    MigrationFileExecution completed = MigrationFileExecution.builder()
      .fileType(MigrationFileType.DEBT_POSITIONS).status(MigrationFileExecutionStatus.EXPORTED).build();
    when(migrationExecutionRepositoryMock.findById(parentId)).thenReturn(Optional.of(previous));
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdOrderByCreationDateAscIdAsc(parentId))
      .thenReturn(List.of(completed));

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("previous checkpoint must equal its exclusive period.toDate at midnight UTC", failure.getMessage());
    verify(migrationExecutionRepositoryMock).findById(parentId);
    verify(migrationFileExecutionRepositoryMock).findAllByMigrationIdOrderByCreationDateAscIdAsc(parentId);
  }

  @Test
  void givenFullWithoutEndWhenStartMigrationThenUsesCurrentUtcMidnight() {
    MigrationRequestDto request = request(MigrationAction.EXPORT);
    LocalDate before = LocalDate.now(ZoneOffset.UTC);
    when(migrationExecutionRepositoryMock.save(isA(MigrationExecution.class))).thenAnswer(invocation -> {
      MigrationExecution saved = invocation.getArgument(0);
      saved.setId(UUID.randomUUID());
      return saved;
    });
    when(migrationActionExecutorMock.execute(same(request), isA(MigrationExecution.class)))
      .thenReturn(CompletableFuture.completedFuture(null));

    service.startMigration(request);

    LocalDate after = LocalDate.now(ZoneOffset.UTC);
    ArgumentCaptor<MigrationExecution> executionCaptor = ArgumentCaptor.forClass(MigrationExecution.class);
    verify(migrationExecutionRepositoryMock).save(executionCaptor.capture());
    verify(migrationActionExecutorMock).execute(same(request), same(executionCaptor.getValue()));
    assertTrue(List.of(before, after).contains(executionCaptor.getValue().getDateTo()));
    assertNull(executionCaptor.getValue().getDateFrom());
  }

  @Test
  void givenEmptyFullWindowWhenStartMigrationThenRejectsBeforeSaving() {
    MigrationRequestDto request = request(MigrationAction.EXPORT);
    LocalDate day = LocalDate.of(2026, Month.JANUARY, 2);
    request.setPeriod(new PeriodDto(day, day));

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> service.startMigration(request));

    assertEquals("extraction period must have a start before its exclusive end", failure.getMessage());
  }

  @Test
  void givenUnknownTransferMigrationWhenStartMigrationThenRejectsRequest() {
    MigrationRequestDto request = request(MigrationAction.TRANSFER);
    when(migrationExecutionRepositoryMock.findById(request.getMigrationId())).thenReturn(Optional.empty());

    InvalidValueException failure = assertThrows(
      InvalidValueException.class, () -> service.startMigration(request)
    );

    assertEquals("migrationId does not identify an existing migration", failure.getMessage());
    verify(migrationExecutionRepositoryMock).findById(request.getMigrationId());
  }

  private MigrationRequestDto request(MigrationAction action) {
    return new MigrationRequestDto()
      .action(action)
      .orchestrationMode(OrchestrationMode.FULL)
      .migrationId(action == MigrationAction.TRANSFER ? UUID.randomUUID() : null)
      .ipaCodes(action == MigrationAction.TRANSFER ? null : List.of("IPA1"))
      .fileTypesToInclude(action == MigrationAction.TRANSFER ? null : List.of(MigrationFileType.ORGANIZATIONS))
      .period(new PeriodDto());
  }

  private MigrationExecution execution(UUID id) {
    return MigrationExecution.builder()
      .id(id)
      .action(MigrationAction.EXPORT)
      .orchestrationMode(OrchestrationMode.FULL)
      .status(MigrationExecutionStatus.COMPLETED)
      .ipacodes(List.of("IPA1"))
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .creationDate(LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0))
      .build();
  }
}
