package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.CompleteMigrationPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.TransferPhaseCoordinator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrchestrateMigrationServiceImpl implements OrchestrateMigrationService {

  private final TransferPhaseCoordinator transferPhaseCoordinator;
  private final CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinator;

  public OrchestrateMigrationServiceImpl(
    TransferPhaseCoordinator transferPhaseCoordinator,
    CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinator
  ) {
    this.transferPhaseCoordinator = transferPhaseCoordinator;
    this.completeMigrationPhaseCoordinator = completeMigrationPhaseCoordinator;
  }

  @Override
  public MigrationResponseDto startMigration(MigrationRequestDto request) {
    MigrationPhase phase = request.getPhase();
    if (phase == null) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "phase is required");
    }

    UUID migrationId;
    MigrationExecutionStatus status;
    switch (phase) {
      case TRANSFER -> {
        migrationId = request.getMigrationId();
        if (migrationId == null) {
          throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required for TRANSFER");
        }
        transferPhaseCoordinator.start(migrationId)
          .whenComplete((ignored, error) -> {
            if (error != null) {
              // TODO: Persist asynchronous coordinator failures in the migration status.
              log.error("Failed to start TRANSFER coordinator for migration {}", migrationId, error);
            }
          });
        status = MigrationExecutionStatus.RUNNING;
      }
      case COMPLETE -> {
        migrationId = UUID.randomUUID();
        completeMigrationPhaseCoordinator.start(migrationId);
        status = MigrationExecutionStatus.RUNNING;
      }
      case EXPORT -> throw new UnsupportedOperationException("EXPORT orchestration is not implemented yet");
      default -> throw new UnsupportedOperationException("Unsupported migration phase");
    }

    return new MigrationResponseDto()
      .migrationId(migrationId)
      .phase(phase)
      .status(status)
      .cycleMode(request.getCycleMode())
      .ipaCodes(phase == MigrationPhase.TRANSFER ? null : request.getIpaCodes())
      .createdAt(OffsetDateTime.now(ZoneOffset.UTC));
  }
}
