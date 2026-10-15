package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Utilities;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MigrationResponseMapper {

  public MigrationResponseDto toMigrationResponseDto(MigrationExecution execution) {
    List<String> ipaCodes = null;
    OffsetDateTime createdAt = Utilities.toUtcOffsetDateTime(execution.getCreationDate());
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
