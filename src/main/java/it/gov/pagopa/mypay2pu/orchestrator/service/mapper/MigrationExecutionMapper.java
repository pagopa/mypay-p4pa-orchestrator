package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.MigrationRequestWithMigrationExecutionModelDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MigrationExecutionMapper {

  public MigrationRequestWithMigrationExecutionModelDto toMigrationRequestWithMigrationExecutionModelDto(MigrationRequestDto request,
                                                                                                         MigrationExecution execution) {
    boolean transfer = request.getAction() == MigrationAction.TRANSFER;
    return MigrationRequestWithMigrationExecutionModelDto.builder()
      .action(request.getAction())
      .orchestrationMode(request.getOrchestrationMode())
      .ipaCodes(List.copyOf(transfer ? execution.getIpacodes() : request.getIpaCodes()))
      .fileTypesToInclude(List.copyOf(transfer ? execution.getFileTypes() : request.getFileTypesToInclude()))
      .period(request.getPeriod())
      .migrationId(execution.getId())
      .logicalKey(request.getLogicalKey())
      .migrationExecution(execution)
      .build();
  }

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
