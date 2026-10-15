package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.config.json.LocalDateTimeToOffsetDateTimeSerializer;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class MigrationResponseMapper {

  public MigrationResponseDto toMigrationResponseDto(MigrationExecution execution) {
    List<String> ipaCodes = null;
    OffsetDateTime createdAt = LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(execution.getCreationDate());
    if (execution.getAction() != MigrationAction.TRANSFER) {
      ipaCodes = execution.getIpacodes();
    }

    return new MigrationResponseDto()
      .migrationId(execution.getId())
      .action(execution.getAction())
      .status(execution.getStatus())
      .orchestationMode(execution.getCycleMode())
      .ipaCodes(ipaCodes)
      .createdAt(createdAt);
  }
}
