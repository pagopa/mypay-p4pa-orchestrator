package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

@Component
public class MigrationExecutionMapper {

  private final String brokerIpaCode;

  public MigrationExecutionMapper(
    @Value("${orchestrator.transfer.broker-ipa-code}") String brokerIpaCode
  ) {
    this.brokerIpaCode = brokerIpaCode;
  }

  public MigrationExecution toMigrationExecution(MigrationRequestDto request) {
    PeriodDto period = request.getPeriod();

    try {
      return MigrationExecution.builder()
        .brokerIpaCode(brokerIpaCode)
        .ipacodes(request.getIpaCodes().toString())
        .cycleMode(request.getOrchestationMode())
        .action(request.getAction())
        .dateFrom(period == null ? null : period.getFromDate())
        .dateTo(period == null ? null : period.getToDate())
        .logicalKeys(request.getLogicalKey())
        .fileTypes(request.getFileTypesToInclude().toString())
        .build();
    } catch (JacksonException e) {
      throw new IllegalStateException("Unable to serialize migration request for persistence", e);
    }
  }
}
