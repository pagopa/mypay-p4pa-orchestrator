package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CompleteMigrationPhaseCoordinator {

  public void start(MigrationExecution execution) {
    log.info("COMPLETE coordinator stub invoked for migration {}", execution.getId());
    // TODO Connect the export coordinator from the parallel implementation before starting transfer.
  }
}
