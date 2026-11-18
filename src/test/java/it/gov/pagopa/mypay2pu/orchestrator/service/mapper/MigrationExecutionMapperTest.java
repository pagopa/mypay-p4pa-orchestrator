package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MigrationExecutionMapperTest {

  private final MigrationExecutionMapper mapper = new MigrationExecutionMapper();

  @Test
  void givenRequestWithTimestampBoundsWhenToMigrationExecutionThenMapsAllFields() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.COMPLETE);
    request.setOrchestrationMode(OrchestrationMode.FULL);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS
    ));
    request.setPeriod(new PeriodDto(
      LocalDateTime.of(2026, Month.JANUARY, 1, 12, 30, 45, 123456000)
        .atZone(Constants.ZONEID).toOffsetDateTime(),
      LocalDateTime.of(2026, Month.DECEMBER, 31, 15, 45, 30, 654321000)
        .atZone(Constants.ZONEID).toOffsetDateTime()
    ));
    request.setLogicalKey("organization");

    MigrationExecution result = mapper.toMigrationExecution(request);

    checkNotNullFields(result);
    assertEquals(List.of("80000000000"), result.getIpacodes());
    assertEquals(OrchestrationMode.FULL, result.getOrchestrationMode());
    assertEquals(MigrationAction.COMPLETE, result.getAction());
    assertEquals(LocalDateTime.of(2026, Month.JANUARY, 1, 12, 30, 45, 123456000), result.getDateFrom());
    assertEquals(LocalDateTime.of(2026, Month.DECEMBER, 31, 15, 45, 30, 654321000), result.getDateTo());
    assertEquals("organization", result.getLogicalKeys());
    assertEquals(List.of(MigrationFileType.ORGANIZATIONS, MigrationFileType.DEBT_POSITIONS), result.getFileTypes());
  }

  @Test
  void givenUtcBoundsInSummerWhenToMigrationExecutionThenConvertsToApplicationZoneWithoutLosingPrecision() {
    OffsetDateTime from = OffsetDateTime.of(2026, Month.JULY.getValue(), 1, 10, 30, 45, 123456000, ZoneOffset.UTC);
    MigrationRequestDto request = new MigrationRequestDto()
      .action(MigrationAction.EXPORT).orchestrationMode(OrchestrationMode.FULL)
      .ipaCodes(List.of("IPA1")).fileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS))
      .logicalKey("organization").period(new PeriodDto(from, from.plusHours(1)));

    MigrationExecution result = mapper.toMigrationExecution(request);

    checkNotNullFields(result);
    assertEquals(LocalDateTime.of(2026, Month.JULY, 1, 12, 30, 45, 123456000), result.getDateFrom());
    assertEquals(LocalDateTime.of(2026, Month.JULY, 1, 13, 30, 45, 123456000), result.getDateTo());
  }

  private void checkNotNullFields(MigrationExecution execution) {
    assertNotNull(execution.getIpacodes());
    assertNotNull(execution.getOrchestrationMode());
    assertNotNull(execution.getAction());
    assertNotNull(execution.getDateFrom());
    assertNotNull(execution.getDateTo());
    assertNotNull(execution.getLogicalKeys());
    assertNotNull(execution.getFileTypes());
  }

}
