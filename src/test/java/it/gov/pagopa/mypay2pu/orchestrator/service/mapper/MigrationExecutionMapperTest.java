package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MigrationExecutionMapperTest {

  private final MigrationExecutionMapper mapper = new MigrationExecutionMapper("BROKER-IPA");

  @Test
  void mapsRequestFieldsToMigrationExecution() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.COMPLETE);
    request.setOrchestationMode(CycleMode.FULL);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS
    ));
    request.setPeriod(new PeriodDto(
      LocalDate.of(2026, Month.JANUARY, 1),
      LocalDate.of(2026, Month.DECEMBER, 31)
    ));
    request.setLogicalKey("organization");

    MigrationExecution result = mapper.toMigrationExecution(request);

    checkNotNullFields(result);
    assertEquals("BROKER-IPA", result.getBrokerIpaCode());
    assertEquals(List.of("80000000000"), result.getIpacodes());
    assertEquals(CycleMode.FULL, result.getCycleMode());
    assertEquals(MigrationAction.COMPLETE, result.getAction());
    assertEquals(LocalDate.of(2026, Month.JANUARY, 1), result.getDateFrom());
    assertEquals(LocalDate.of(2026, Month.DECEMBER, 31), result.getDateTo());
    assertEquals("organization", result.getLogicalKeys());
    assertEquals(List.of("ORGANIZATIONS", "DEBT_POSITIONS"), result.getFileTypes());
  }

  private void checkNotNullFields(MigrationExecution execution) {
    assertNotNull(execution.getBrokerIpaCode());
    assertNotNull(execution.getIpacodes());
    assertNotNull(execution.getCycleMode());
    assertNotNull(execution.getAction());
    assertNotNull(execution.getDateFrom());
    assertNotNull(execution.getDateTo());
    assertNotNull(execution.getLogicalKeys());
    assertNotNull(execution.getFileTypes());
  }
}
