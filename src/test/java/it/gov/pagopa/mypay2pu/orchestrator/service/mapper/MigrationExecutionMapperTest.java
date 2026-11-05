package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.dto.MigrationRequestWithMigrationExecutionModelDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MigrationExecutionMapperTest {

  private final MigrationExecutionMapper mapper = new MigrationExecutionMapper();

  @Test
  void mapsRequestFieldsToMigrationExecution() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.COMPLETE);
    request.setOrchestrationMode(OrchestrationMode.FULL);
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
    assertEquals(List.of("80000000000"), result.getIpacodes());
    assertEquals(OrchestrationMode.FULL, result.getOrchestrationMode());
    assertEquals(MigrationAction.COMPLETE, result.getAction());
    assertEquals(LocalDate.of(2026, Month.JANUARY, 1), result.getDateFrom());
    assertEquals(LocalDate.of(2026, Month.DECEMBER, 31), result.getDateTo());
    assertEquals("organization", result.getLogicalKeys());
    assertEquals(List.of(MigrationFileType.ORGANIZATIONS, MigrationFileType.DEBT_POSITIONS), result.getFileTypes());
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

  @ParameterizedTest
  @EnumSource(MigrationAction.class)
  void givenRequestAndSavedExecutionWhenToMigrationRequestWithMigrationExecutionModelDtoThenMapsContext(
    MigrationAction action
  ) {
    MigrationExecution execution = MigrationExecution.builder()
      .id(UUID.randomUUID())
      .ipacodes(List.of("saved-ipa"))
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .build();
    MigrationRequestDto request = new MigrationRequestDto()
      .action(action)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipaCodes(List.of("request-ipa"))
      .fileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS))
      .migrationId(UUID.randomUUID())
      .logicalKey("logical-key")
      .period(new PeriodDto());

    MigrationRequestWithMigrationExecutionModelDto result =
      mapper.toMigrationRequestWithMigrationExecutionModelDto(request, execution);

    checkNotNullFields(result);
    assertSame(execution, result.getMigrationExecution());
    assertEquals(execution.getId(), result.getMigrationId());
    assertEquals(action, result.getAction());
    assertEquals(request.getOrchestrationMode(), result.getOrchestrationMode());
    assertEquals(request.getPeriod(), result.getPeriod());
    assertEquals(request.getLogicalKey(), result.getLogicalKey());
    assertEquals(
      action == MigrationAction.TRANSFER ? execution.getIpacodes() : request.getIpaCodes(),
      result.getIpaCodes()
    );
    assertEquals(
      action == MigrationAction.TRANSFER ? execution.getFileTypes() : request.getFileTypesToInclude(),
      result.getFileTypesToInclude()
    );
  }

  private void checkNotNullFields(MigrationRequestWithMigrationExecutionModelDto request) {
    assertNotNull(request.getAction());
    assertNotNull(request.getOrchestrationMode());
    assertNotNull(request.getIpaCodes());
    assertNotNull(request.getFileTypesToInclude());
    assertNotNull(request.getPeriod());
    assertNotNull(request.getMigrationId());
    assertNotNull(request.getLogicalKey());
    assertNotNull(request.getMigrationExecution());
  }
}
