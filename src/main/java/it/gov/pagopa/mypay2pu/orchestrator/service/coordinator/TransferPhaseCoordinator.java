package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Coordinates the transfer phase of a migration.
 *
 * <p>It transfers extracted files in dependency order, submitting each file to the migration
 * service with the action selected by the cycle mode: CREATE for FULL and UPDATE for DELTA or
 * REJECTS_ONLY. It persists the returned workflow identifiers and delegates status polling and
 * outcome collection to the evidence-collection flow.
 *
 * <p>Transfer proceeds as soon as the next dependency-ready file is available, without waiting for
 * unrelated extractions to finish. Extraction and evidence collection remain the responsibility
 * of their respective flows.
 */
@Component
@Slf4j
public class TransferPhaseCoordinator {

  /**
   * Starts transfer coordination for an existing migration.
   *
   * <p>The start routine runs on the orchestrator executor. The returned future tracks its
   * completion, not the completion of the migration itself.
   *
   * @param execution persisted migration whose extracted files should be transferred
   * @return a future completed when the transfer startup routine finishes
   */
  @Async("orchestratorTaskExecutor")
  public CompletableFuture<Void> start(MigrationExecution execution) {
    // TODO: Submit dependency-ready files, persist workflow IDs, and connect workflow outcome
    // collection to migration status updates.
    log.info("TRANSFER coordinator stub invoked for migration {}", execution.getId());
    return CompletableFuture.completedFuture(null);
  }
}
