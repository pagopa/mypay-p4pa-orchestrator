package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.util.Optional;
import java.util.UUID;

public interface MigrationExecutionActivity {

  MigrationExecution save(MigrationExecution execution);

  Optional<MigrationExecution> findById(UUID id);
}
