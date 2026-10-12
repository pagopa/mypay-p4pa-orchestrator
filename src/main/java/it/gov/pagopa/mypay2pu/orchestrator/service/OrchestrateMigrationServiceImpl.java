package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.AllMigrationPhaseCoordinator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrchestrateMigrationServiceImpl implements OrchestrateMigrationService {

  private final AllMigrationPhaseCoordinator allMigrationPhaseCoordinator;

  public OrchestrateMigrationServiceImpl(AllMigrationPhaseCoordinator allMigrationPhaseCoordinator) {
    this.allMigrationPhaseCoordinator = allMigrationPhaseCoordinator;
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
        status = MigrationExecutionStatus.RUNNING;
      }
      case ALL -> {
        migrationId = UUID.randomUUID();
        allMigrationPhaseCoordinator.start(migrationId);
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
