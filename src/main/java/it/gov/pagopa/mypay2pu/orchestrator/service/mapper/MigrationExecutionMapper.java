package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import org.springframework.stereotype.Component;

@Component
public class MigrationExecutionMapper {

  public MigrationExecution toMigrationExecution(MigrationRequestDto request) {
    PeriodDto period = request.getPeriod();

    return MigrationExecution.builder()
      .ipacodes(request.getIpaCodes())
      .orchestrationMode(request.getOrchestrationMode())
      .action(request.getAction())
      .dateFrom(period == null ? null : period.getFromDate())
      .dateTo(period == null ? null : period.getToDate())
      .logicalKeys(request.getLogicalKey())
      .fileTypes(request.getFileTypesToInclude())
      .build();
  }
}
