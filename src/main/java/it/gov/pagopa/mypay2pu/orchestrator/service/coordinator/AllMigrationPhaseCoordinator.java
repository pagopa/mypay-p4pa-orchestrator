package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AllMigrationPhaseCoordinator {

  public void start(UUID migrationId) {
    log.info("ALL coordinator stub invoked for migration {}", migrationId);
    // TODO Connect the export coordinator from the parallel implementation before starting transfer.
  }
}
