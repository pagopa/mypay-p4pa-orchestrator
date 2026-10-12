package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
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
   * @param migrationId identifier of the migration whose extracted files should be transferred
   * @return {@link MigrationExecutionStatus#RUNNING} while the transfer is in progress
   */
  public MigrationExecutionStatus start(UUID migrationId) {
    // TODO: Implement transfer orchestration and workflow tracking.
    log.info("TRANSFER coordinator stub invoked for migration {}", migrationId);
    return MigrationExecutionStatus.RUNNING;
  }
}
