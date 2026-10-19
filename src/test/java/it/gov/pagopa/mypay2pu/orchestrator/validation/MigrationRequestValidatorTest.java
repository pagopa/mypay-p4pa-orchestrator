package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationRequestValidatorTest {
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;

  private MigrationRequestValidator validator;

  @BeforeEach
  void setUp() {
    validator = new MigrationRequestValidator(migrationExecutionRepositoryMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationExecutionRepositoryMock);
  }

  @Test
  void requestWithoutActionIsRejected() {
    MigrationRequestDto request = new MigrationRequestDto();

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validate(request)
    );

    assertEquals("action is required", exception.getMessage());
  }

  @Test
  void requestWithoutOrchestratorModeIsRejected() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("orchestrationMode is required", exception.getMessage());
  }

  @Test
  void exportAndCompleteActionsRequireIpaCodes() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = new MigrationRequestDto();
      request.setAction(action);
      request.setOrchestrationMode(OrchestrationMode.DELTA);
      request.setIpaCodes(null);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
      assertEquals("ipaCodes are required and must not be empty", exception.getMessage());
    }
  }

  @Test
  void exportAndCompleteActionsRequireFileTypes() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = new MigrationRequestDto();
      request.setAction(action);
      request.setOrchestrationMode(OrchestrationMode.DELTA);
      request.setIpaCodes(List.of("80000000000"));
      request.setFileTypesToInclude(null);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
      assertEquals("fileTypesToInclude are required and must not be empty", exception.getMessage());
    }
  }

  @Test
  void transferRequiresMigrationIdButNoExportFields() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setOrchestrationMode(OrchestrationMode.FULL);
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validate(request)
    );
    assertEquals("migrationId is required for TRANSFER", exception.getMessage());

    UUID migrationId = UUID.randomUUID();
    request.setMigrationId(migrationId);
    MigrationExecution execution = MigrationExecution.builder()
      .id(migrationId)
      .status(MigrationExecutionStatus.COMPLETED)
      .action(MigrationAction.EXPORT)
      .build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));
    assertDoesNotThrow(() -> validator.validate(request));
    assertSame(execution, validator.requireTransferExecution(request));
    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  @Test
  void transferRejectsMigrationThatIsNotCompleted() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(migrationId);
    request.setOrchestrationMode(OrchestrationMode.DELTA);
    MigrationExecution execution = MigrationExecution.builder()
      .id(migrationId)
      .status(MigrationExecutionStatus.RUNNING)
      .action(MigrationAction.EXPORT)
      .build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));

    assertDoesNotThrow(() -> validator.validate(request));
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.requireTransferExecution(request)
    );

    assertEquals("migrationId must identify a completed EXPORT migration for TRANSFER", exception.getMessage());
    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  @Test
  void transferRejectsCompletedMigrationThatWasNotExported() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(migrationId);
    request.setOrchestrationMode(OrchestrationMode.DELTA);
    MigrationExecution execution = MigrationExecution.builder()
      .id(migrationId)
      .status(MigrationExecutionStatus.COMPLETED)
      .action(MigrationAction.COMPLETE)
      .build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.requireTransferExecution(request)
    );

    assertEquals("migrationId must identify a completed EXPORT migration for TRANSFER", exception.getMessage());
    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  @Test
  void transferRejectsRejectsOnlyMode() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(UUID.randomUUID());
    request.setOrchestrationMode(OrchestrationMode.REJECTS_ONLY);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("orchestrationMode REJECTS_ONLY is not supported for TRANSFER", exception.getMessage());
  }

  @Test
  void fullCycleRequiresPeriod() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = request(action, OrchestrationMode.FULL);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

      assertEquals("period is required when orchestrationMode is FULL", exception.getMessage());

      request.setPeriod(new PeriodDto());
      assertDoesNotThrow(() -> validator.validate(request));
    }
  }

  @Test
  void deltaCycleDoesNotRequirePeriod() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      assertDoesNotThrow(() -> validator.validate(request(action, OrchestrationMode.DELTA)));
    }
  }

  @Test
  void fullCycleRejectsInvertedPeriodAndAllowsOptionalEndpoints() {
    MigrationRequestDto request = request(MigrationAction.EXPORT, OrchestrationMode.FULL);
    request.setPeriod(new PeriodDto(
      java.time.LocalDate.of(2026, java.time.Month.FEBRUARY, 1),
      java.time.LocalDate.of(2026, java.time.Month.JANUARY, 1)
    ));

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
    assertEquals("period.fromDate must not be after period.toDate", exception.getMessage());

    request.setPeriod(new PeriodDto());
    assertDoesNotThrow(() -> validator.validate(request));
    request.setPeriod(new PeriodDto(java.time.LocalDate.of(2026, java.time.Month.JANUARY, 1), null));
    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void rejectsOnlyCycleRequiresPreviousMigrationId() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = request(action, OrchestrationMode.REJECTS_ONLY);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

      assertEquals("migrationId is required when orchestrationMode is REJECTS_ONLY", exception.getMessage());

      request.setMigrationId(UUID.randomUUID());
      assertDoesNotThrow(() -> validator.validate(request));
    }
  }

  @Test
  void rejectsOnlyCanResolvePreviousMigration() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = request(MigrationAction.COMPLETE, OrchestrationMode.REJECTS_ONLY);
    request.setMigrationId(migrationId);
    MigrationExecution execution = MigrationExecution.builder().id(migrationId).build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));

    assertSame(execution, validator.requirePreviousMigration(request));

    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  private MigrationRequestDto request(MigrationAction action, OrchestrationMode orchestrationMode) {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(action);
    request.setOrchestrationMode(orchestrationMode);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    return request;
  }
}
