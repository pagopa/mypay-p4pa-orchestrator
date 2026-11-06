package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.MigrationRequestWithMigrationExecutionModelDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationActionExecutorTest {

  @Mock
  private MigrationFacadeService migrationFacadeServiceMock;
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;

  private MigrationActionExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new MigrationActionExecutor(migrationFacadeServiceMock, migrationExecutionRepositoryMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationFacadeServiceMock, migrationExecutionRepositoryMock);
  }

  @ParameterizedTest
  @EnumSource(MigrationAction.class)
  void givenSuccessfulActionWhenExecuteThenPersistsCompletedMigration(MigrationAction action) {
    MigrationRequestWithMigrationExecutionModelDto request = request(action);
    MigrationExecution execution = request.getMigrationExecution();

    assertTrue(executor.execute(request).isDone());

    assertEquals(MigrationExecutionStatus.COMPLETED, execution.getStatus());
    assertEquals(1, execution.getFileTypeCountCompleted());
    assertNull(execution.getErrorCode());
    assertNull(execution.getErrorMsg());
    assertEquals(execution.getId(), request.getMigrationRequest().getMigrationId());
    verify(migrationFacadeServiceMock).execute(request.getMigrationRequest());
    verify(migrationExecutionRepositoryMock).save(execution);
  }

  @ParameterizedTest
  @EnumSource(MigrationAction.class)
  void givenActionFailureWhenExecuteThenPersistsFailureAndPropagates(MigrationAction action) {
    MigrationRequestWithMigrationExecutionModelDto request = request(action);
    IllegalStateException failure = new IllegalStateException("operation failed");
    doThrow(failure).when(migrationFacadeServiceMock).execute(request.getMigrationRequest());

    assertSame(failure, assertThrows(IllegalStateException.class, () -> executor.execute(request)));

    assertEquals(MigrationExecutionStatus.FAILED, request.getMigrationExecution().getStatus());
    assertEquals(action + "_FAILED", request.getMigrationExecution().getErrorCode());
    assertEquals("operation failed", request.getMigrationExecution().getErrorMsg());
    verify(migrationFacadeServiceMock).execute(request.getMigrationRequest());
    verify(migrationExecutionRepositoryMock).save(request.getMigrationExecution());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void givenFailurePersistenceErrorWhenExecuteThenPreservesOriginalFailure(String message) {
    MigrationRequestWithMigrationExecutionModelDto request = request(MigrationAction.EXPORT);
    IllegalStateException failure = new IllegalStateException(message);
    IllegalArgumentException persistenceFailure = new IllegalArgumentException("database unavailable");
    doThrow(failure).when(migrationFacadeServiceMock).execute(request.getMigrationRequest());
    when(migrationExecutionRepositoryMock.save(request.getMigrationExecution())).thenThrow(persistenceFailure);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> executor.execute(request));

    assertSame(failure, thrown);
    assertEquals(List.of(persistenceFailure), List.of(thrown.getSuppressed()));
    assertEquals("IllegalStateException", request.getMigrationExecution().getErrorMsg());
    verify(migrationFacadeServiceMock).execute(request.getMigrationRequest());
    verify(migrationExecutionRepositoryMock).save(request.getMigrationExecution());
  }

  private MigrationRequestWithMigrationExecutionModelDto request(MigrationAction action) {
    return new MigrationRequestWithMigrationExecutionModelDto(
      new MigrationRequestDto().action(action),
      MigrationExecution.builder()
        .id(UUID.randomUUID())
        .action(action)
        .status(MigrationExecutionStatus.RUNNING)
        .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
        .errorCode("previous error")
        .errorMsg("previous message")
        .build());
  }
}
