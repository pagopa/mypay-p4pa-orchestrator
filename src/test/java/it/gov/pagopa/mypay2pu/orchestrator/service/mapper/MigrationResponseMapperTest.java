package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MigrationResponseMapperTest {

  private final MigrationResponseMapper mapper = new MigrationResponseMapper(new ObjectMapper());

  @Test
  void mapsCompleteExecutionToResponse() {
    UUID migrationId = UUID.randomUUID();
    MigrationExecution execution = MigrationExecution.builder()
      .id(migrationId)
      .status(MigrationExecutionStatus.RUNNING)
      .cycleMode(CycleMode.FULL)
      .ipacodes("[\"80000000000\"]")
      .build();

    MigrationResponseDto result = mapper.toMigrationResponseDto(execution, MigrationPhase.COMPLETE);

    checkNotNullFields(result);
    assertEquals(migrationId, result.getMigrationId());
    assertEquals(MigrationPhase.COMPLETE, result.getPhase());
    assertEquals(MigrationExecutionStatus.RUNNING, result.getStatus());
    assertEquals(CycleMode.FULL, result.getCycleMode());
    assertEquals(List.of("80000000000"), result.getIpaCodes());
    assertEquals(List.of(), result.getUploadIds());
  }

  @Test
  void omitsIpaCodesForTransferResponse() {
    MigrationExecution execution = MigrationExecution.builder()
      .id(UUID.randomUUID())
      .status(MigrationExecutionStatus.RUNNING)
      .cycleMode(CycleMode.DELTA)
      .ipacodes("[\"80000000000\"]")
      .build();

    MigrationResponseDto result = mapper.toMigrationResponseDto(execution, MigrationPhase.TRANSFER);

    assertEquals(MigrationPhase.TRANSFER, result.getPhase());
    assertNull(result.getIpaCodes());
  }

  private void checkNotNullFields(MigrationResponseDto response) {
    assertNotNull(response.getMigrationId());
    assertNotNull(response.getPhase());
    assertNotNull(response.getStatus());
    assertNotNull(response.getCycleMode());
    assertNotNull(response.getIpaCodes());
    assertNotNull(response.getCreatedAt());
    assertNotNull(response.getUploadIds());
  }
}
