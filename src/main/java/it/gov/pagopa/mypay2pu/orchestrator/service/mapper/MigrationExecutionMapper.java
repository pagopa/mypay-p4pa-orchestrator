package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Component
public class MigrationExecutionMapper {

  public MigrationExecution toMigrationExecution(MigrationRequestDto request) {
    PeriodDto period = request.getPeriod();

    return MigrationExecution.builder()
      .ipacodes(request.getIpaCodes())
      .orchestrationMode(request.getOrchestrationMode())
      .action(request.getAction())
      .dateFrom(period == null ? null : toLocalDateTime(period.getFromDate()))
      .dateTo(period == null ? null : toLocalDateTime(period.getToDate()))
      .logicalKeys(request.getLogicalKey())
      .fileTypes(request.getFileTypesToInclude())
      .build();
  }

  private LocalDateTime toLocalDateTime(OffsetDateTime offsetDateTime) {
    return offsetDateTime == null ? null : offsetDateTime.atZoneSameInstant(Constants.ZONEID).toLocalDateTime();
  }
}
