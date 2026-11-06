package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.MigrationRequestWithMigrationExecutionModelDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
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

  private OrchestrateMigrationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new OrchestrateMigrationServiceImpl(
      migrationActionExecutorMock,
      new MigrationRequestValidator(migrationExecutionRepositoryMock),
      migrationExecutionRepositoryMock,
      new MigrationExecutionMapper(),
      new MigrationResponseMapper()
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationActionExecutorMock, migrationExecutionRepositoryMock);
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
    when(migrationActionExecutorMock.execute(isA(MigrationRequestWithMigrationExecutionModelDto.class)))
      .thenAnswer(invocation -> {
        saved.setStatus(MigrationExecutionStatus.COMPLETED);
        return CompletableFuture.completedFuture(null);
      });

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
    ArgumentCaptor<MigrationRequestWithMigrationExecutionModelDto> requestCaptor =
      ArgumentCaptor.forClass(MigrationRequestWithMigrationExecutionModelDto.class);
    calls.verify(migrationActionExecutorMock).execute(requestCaptor.capture());
    assertSame(saved, requestCaptor.getValue().getMigrationExecution());
    assertSame(request, requestCaptor.getValue().getMigrationRequest());
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
    when(migrationActionExecutorMock.execute(isA(MigrationRequestWithMigrationExecutionModelDto.class)))
      .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("action failed")));

    MigrationResponseDto response = service.startMigration(request);

    ArgumentCaptor<MigrationRequestWithMigrationExecutionModelDto> captor =
      ArgumentCaptor.forClass(MigrationRequestWithMigrationExecutionModelDto.class);
    verify(migrationActionExecutorMock).execute(captor.capture());
    assertEquals(parentId, captor.getValue().getMigrationExecution().getParentExecutionId());
    assertSame(request, captor.getValue().getMigrationRequest());
    assertEquals(response.getMigrationId(), captor.getValue().getMigrationExecution().getId());
    assertEquals(parentId, request.getMigrationId());
    verify(migrationExecutionRepositoryMock).findById(parentId);
    verify(migrationExecutionRepositoryMock).save(captor.getValue().getMigrationExecution());
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
      .status(MigrationExecutionStatus.COMPLETED)
      .ipacodes(List.of("IPA1"))
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .creationDate(LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0))
      .build();
  }
}
