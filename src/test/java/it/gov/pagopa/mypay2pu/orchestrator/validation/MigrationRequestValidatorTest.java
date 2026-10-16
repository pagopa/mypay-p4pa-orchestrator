package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
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

    assertEquals("orchestationMode is required", exception.getMessage());
  }

  @Test
  void exportAndCompleteActionsRequireIpaCodes() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = new MigrationRequestDto();
      request.setAction(action);
      request.setOrchestationMode(CycleMode.DELTA);
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
      request.setOrchestationMode(CycleMode.DELTA);
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
    request.setOrchestationMode(CycleMode.FULL);
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validate(request)
    );
    assertEquals("migrationId is required for TRANSFER", exception.getMessage());

    UUID migrationId = UUID.randomUUID();
    request.setMigrationId(migrationId);
    MigrationExecution execution = MigrationExecution.builder().id(migrationId).build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));
    assertDoesNotThrow(() -> validator.validate(request));
    assertSame(execution, validator.requireTransferExecution(request));
    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  @Test
  void transferRejectsRejectsOnlyMode() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(UUID.randomUUID());
    request.setOrchestationMode(CycleMode.REJECTS_ONLY);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("orchestationMode REJECTS_ONLY is not supported for TRANSFER", exception.getMessage());
  }

  @Test
  void fullCycleRequiresPeriod() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = request(action, CycleMode.FULL);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

      assertEquals("period is required when orchestationMode is FULL", exception.getMessage());

      request.setPeriod(new PeriodDto());
      assertDoesNotThrow(() -> validator.validate(request));
    }
  }

  @Test
  void deltaCycleDoesNotRequirePeriod() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      assertDoesNotThrow(() -> validator.validate(request(action, CycleMode.DELTA)));
    }
  }

  @Test
  void rejectsOnlyCycleRequiresPreviousMigrationId() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = request(action, CycleMode.REJECTS_ONLY);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

      assertEquals("migrationId is required when orchestationMode is REJECTS_ONLY", exception.getMessage());

      request.setMigrationId(UUID.randomUUID());
      assertDoesNotThrow(() -> validator.validate(request));
    }
  }

  @Test
  void rejectsOnlyCanResolvePreviousMigration() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = request(MigrationAction.COMPLETE, CycleMode.REJECTS_ONLY);
    request.setMigrationId(migrationId);
    MigrationExecution execution = MigrationExecution.builder().id(migrationId).build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));

    assertSame(execution, validator.requirePreviousMigration(request));

    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  private MigrationRequestDto request(MigrationAction action, CycleMode cycleMode) {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(action);
    request.setOrchestationMode(cycleMode);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    return request;
  }
}
