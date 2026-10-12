package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransferPhaseCoordinator {

  public MigrationExecutionStatus start(UUID migrationId) {
    log.info("TRANSFER coordinator stub invoked for migration {}", migrationId);
    return MigrationExecutionStatus.RUNNING;
  }
}
