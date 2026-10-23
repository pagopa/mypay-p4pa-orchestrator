package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Starts export coordination for a persisted migration. */
@Component
@Slf4j
public class ExportPhaseCoordinator {

  @Async("orchestratorTaskExecutor")
  public CompletableFuture<Void> start(MigrationExecution execution) {
    // TODO: Resolve selected file types and submit dependency-ready extraction requests.
    log.info("EXPORT coordinator stub invoked for migration {}", execution.getId());
    return CompletableFuture.completedFuture(null);
  }
}
