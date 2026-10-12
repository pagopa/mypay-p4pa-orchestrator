package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Coordinates the transfer phase of a migration.
 *
 * <p>When implemented, this coordinator will wait for extracted files to become available, transfer
 * them in dependency order, and submit them to the migration service using the action selected by
 * the migration cycle mode. It will persist the returned workflow identifiers and hand off status
 * polling and outcome collection to the evidence-collection flow. Transfer should proceed as soon
 * as the next file is ready, without waiting for unrelated extractions to finish.
 *
 * <p>Extraction and evidence collection are owned by their respective flows; this coordinator
 * orchestrates transfer and its dependencies only.
 */
@Component
@Slf4j
public class TransferPhaseCoordinator {

  /**
   * Starts transfer orchestration for an existing migration.
   *
   * @param migrationId identifier of the migration whose extracted files should be transferred
   * @return the current migration execution status
   */
  public MigrationExecutionStatus start(UUID migrationId) {
    log.info("TRANSFER coordinator stub invoked for migration {}", migrationId);
    return MigrationExecutionStatus.RUNNING;
  }
}
