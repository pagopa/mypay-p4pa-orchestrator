package it.gov.pagopa.mypay2pu.orchestrator.dto;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@NoArgsConstructor
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class MigrationRequestWithMigrationExecutionModelDto {
  MigrationRequestDto migrationRequest;
  MigrationExecution migrationExecution;
}
