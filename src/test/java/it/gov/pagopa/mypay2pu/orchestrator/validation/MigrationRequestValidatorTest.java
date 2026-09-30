package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.FileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.openapitools.jackson.nullable.JsonNullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationRequestValidatorTest {
  private final MigrationRequestValidator validator = new MigrationRequestValidator();

  @Test
  void exportRequiresIpaCodes() {
    MigrationRequestDto request = exportRequest();
    request.setIpaCodes(null);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("ipaCodes are required and must not be empty", exception.getMessage());
  }

  @Test
  void transferRequiresMigrationIdButNoExportFields() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.TRANSFER);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
    assertEquals("migrationId is required for TRANSFER", exception.getMessage());

    request.setMigrationId(UUID.randomUUID());
    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void fullCycleRequiresPeriod() {
    MigrationRequestDto request = exportRequest();
    request.setCycleMode(CycleMode.FULL);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("period is required when cycleMode is FULL", exception.getMessage());
  }

  @Test
  void rejectsOnlyCycleRequiresRejectsPayload() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.TRANSFER);
    request.setMigrationId(UUID.randomUUID());
    request.setCycleMode(CycleMode.REJECTS_ONLY);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("rejectsPayload is required when cycleMode is REJECTS_ONLY", exception.getMessage());

    request.setRejectsPayload(JsonNullable.of(Map.of()));
    assertDoesNotThrow(() -> validator.validate(request));
  }

  private MigrationRequestDto exportRequest() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.EXPORT);
    request.setIpaCodes(List.of("ipa-code"));
    request.setCycleMode(CycleMode.DELTA);
    request.setFileTypesToInclude(List.of(FileType.ORGANIZATIONS));
    return request;
  }
}
