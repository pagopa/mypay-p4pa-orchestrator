package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import org.springframework.stereotype.Service;

@Service
public class OrchestrateMigrationServiceImpl implements OrchestrateMigrationService {

  @Override
  public MigrationResponseDto startMigration(MigrationRequestDto request) {
    throw new UnsupportedOperationException("Migration orchestration is not implemented yet");
  }
}
