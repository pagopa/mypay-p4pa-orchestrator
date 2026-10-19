package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.CompleteMigrationPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.ExportPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.TransferPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationExecutionMapper;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationResponseMapper;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrchestrateMigrationServiceImpl implements OrchestrateMigrationService {

  private static final int INITIAL_CYCLE_NUMBER = 1;

  private final ExportPhaseCoordinator exportPhaseCoordinator;
  private final TransferPhaseCoordinator transferPhaseCoordinator;
  private final CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinator;
  private final MigrationRequestValidator migrationRequestValidator;
  private final MigrationExecutionRepository migrationExecutionRepository;
  private final MigrationExecutionMapper migrationExecutionMapper;
  private final MigrationResponseMapper migrationResponseMapper;

  public OrchestrateMigrationServiceImpl(
    ExportPhaseCoordinator exportPhaseCoordinator,
    TransferPhaseCoordinator transferPhaseCoordinator,
    CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinator,
    MigrationRequestValidator migrationRequestValidator,
    MigrationExecutionRepository migrationExecutionRepository,
    MigrationExecutionMapper migrationExecutionMapper,
    MigrationResponseMapper migrationResponseMapper
  ) {
    this.exportPhaseCoordinator = exportPhaseCoordinator;
    this.transferPhaseCoordinator = transferPhaseCoordinator;
    this.completeMigrationPhaseCoordinator = completeMigrationPhaseCoordinator;
    this.migrationRequestValidator = migrationRequestValidator;
    this.migrationExecutionRepository = migrationExecutionRepository;
    this.migrationExecutionMapper = migrationExecutionMapper;
    this.migrationResponseMapper = migrationResponseMapper;
  }

  @Override
  public MigrationResponseDto startMigration(MigrationRequestDto request) {
    migrationRequestValidator.validate(request);
    MigrationAction action = request.getAction();

    MigrationExecution execution = actionRequest2MigrationExecution(request);
    execution.setStatus(MigrationExecutionStatus.RUNNING);
    MigrationExecution savedExecution = migrationExecutionRepository.save(execution);

    UUID migrationId = savedExecution.getId();

    switch (action) {
      case EXPORT -> exportPhaseCoordinator.start(savedExecution)
        .whenComplete((ignored, error) -> {
          if (error != null) {
            // TODO: Persist asynchronous coordinator failures in the migration status.
            log.error("Failed to start EXPORT coordinator for migration {}", migrationId, error);
          }
        });
      case TRANSFER -> transferPhaseCoordinator.start(savedExecution)
        .whenComplete((ignored, error) -> {
          if (error != null) {
            // TODO: Persist asynchronous coordinator failures in the migration status.
            log.error("Failed to start TRANSFER coordinator for migration {}", migrationId, error);
          }
        });
      case COMPLETE -> completeMigrationPhaseCoordinator.start(savedExecution);
      default -> throw new UnsupportedOperationException("Unsupported migration phase");
    }

    return migrationResponseMapper.toMigrationResponseDto(savedExecution);
  }

  private MigrationExecution actionRequest2MigrationExecution(MigrationRequestDto request) {
    return switch (request.getAction()) {
      case TRANSFER -> {
        MigrationExecution existingExecution = migrationRequestValidator.requireTransferExecution(request);
        existingExecution.setOrchestrationMode(request.getOrchestrationMode());
        existingExecution.setAction(request.getAction());
        yield existingExecution;
      }
      case EXPORT, COMPLETE -> newMigrationExecution(request);
    };
  }

  private MigrationExecution newMigrationExecution(MigrationRequestDto request) {
    MigrationExecution newExecution = migrationExecutionMapper.toMigrationExecution(request);
    if (request.getOrchestrationMode() == OrchestrationMode.REJECTS_ONLY) {
      MigrationExecution previousExecution = migrationRequestValidator.requirePreviousMigration(request);
      newExecution.setParentExecutionId(previousExecution.getId());
    }
    newExecution.setCycleNumber(INITIAL_CYCLE_NUMBER);
    newExecution.setCreationDate(LocalDateTime.now(ZoneOffset.UTC));
    return newExecution;
  }
}
