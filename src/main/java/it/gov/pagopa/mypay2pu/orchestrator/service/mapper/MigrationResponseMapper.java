package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.config.json.LocalDateTimeToOffsetDateTimeSerializer;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class MigrationResponseMapper {

  public MigrationResponseDto toMigrationResponseDto(MigrationExecution execution) {
    OffsetDateTime createdAt = LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(execution.getCreationDate());

    return new MigrationResponseDto()
      .migrationId(execution.getId())
      .action(execution.getAction())
      .status(execution.getStatus())
      .orchestrationMode(execution.getOrchestrationMode())
      .ipaCodes(execution.getIpacodes())
      .createdAt(createdAt);
  }
}
