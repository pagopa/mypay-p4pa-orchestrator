package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationRequestValidatorTest {
  private final MigrationRequestValidator validator = new MigrationRequestValidator();

  @Test
  void requestWithoutPhaseDoesNotRequireTransferOrExportFields() {
    assertDoesNotThrow(() -> validator.validate(new MigrationRequestDto()));
  }

  @Test
  void exportAndAllPhasesRequireIpaCodes() {
    for (MigrationPhase phase : List.of(MigrationPhase.EXPORT, MigrationPhase.ALL)) {
      MigrationRequestDto request = new MigrationRequestDto();
      request.setPhase(phase);
      request.setCycleMode(CycleMode.DELTA);
      request.setIpaCodes(null);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
      assertEquals("ipaCodes are required and must not be empty", exception.getMessage());
    }
  }

  @Test
  void transferRequiresMigrationIdButNoExportFields() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.TRANSFER);
    request.setCycleMode(CycleMode.DELTA);
    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
    assertEquals("migrationId is required for TRANSFER", exception.getMessage());

    request.setMigrationId(UUID.randomUUID());
    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void fullCycleRequiresPeriod() {
    MigrationRequestDto request = transferRequest();
    request.setCycleMode(CycleMode.FULL);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("period is required when cycleMode is FULL", exception.getMessage());

    request.setPeriod(new PeriodDto());
    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void deltaCycleDoesNotRequirePeriod() {
    MigrationRequestDto request = transferRequest();
    request.setCycleMode(CycleMode.DELTA);

    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void rejectsOnlyCycleRequiresRejectsPayload() {
    MigrationRequestDto request = transferRequest();
    request.setCycleMode(CycleMode.REJECTS_ONLY);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("rejectsPayload is required when cycleMode is REJECTS_ONLY", exception.getMessage());

    request.setRejectsPayload(List.of("rejected-item"));
    assertDoesNotThrow(() -> validator.validate(request));
  }

  private MigrationRequestDto transferRequest() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.TRANSFER);
    request.setMigrationId(UUID.randomUUID());
    return request;
  }
}
