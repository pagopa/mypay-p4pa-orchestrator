package it.gov.pagopa.mypay2pu.orchestrator.dto;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MigrationRequestWithMigrationExecutionModelDto {
  private final MigrationRequestDto migrationRequest;
  private final MigrationExecution migrationExecution;
}
