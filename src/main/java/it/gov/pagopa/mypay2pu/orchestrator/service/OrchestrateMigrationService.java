package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;

public interface OrchestrateMigrationService {
  MigrationResponseDto startMigration(MigrationRequestDto request);
}
