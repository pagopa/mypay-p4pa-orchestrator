package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferPhaseException;
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
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrchestrateMigrationServiceImpl implements OrchestrateMigrationService {

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
    MigrationResponseDto initialResponse = migrationResponseMapper.toMigrationResponseDto(savedExecution);

    switch (action) {
      case EXPORT -> exportPhaseCoordinator.start(savedExecution)
        .whenComplete((ignored, error) -> {
          if (error != null) {
            // TODO: Persist asynchronous coordinator failures in the migration status.
            log.error("Failed to start EXPORT coordinator for migration {}", migrationId, error);
          }
        });
      case TRANSFER -> transferPhaseCoordinator.start(savedExecution)
        .whenComplete((ignored, error) -> handleTransferCompletion(savedExecution, error));
      case COMPLETE -> completeMigrationPhaseCoordinator.start(savedExecution);
      default -> throw new UnsupportedOperationException("Unsupported migration phase");
    }

    return initialResponse;
  }

  private void handleTransferCompletion(MigrationExecution execution, Throwable error) {
    if (error != null) {
      Throwable cause = unwrapCompletionException(error);
      execution.setStatus(MigrationExecutionStatus.FAILED);
      execution.setErrorCode(cause instanceof TransferPhaseException transferPhaseException
        ? transferPhaseException.getCode()
        : "TRANSFER_FAILED");
      execution.setErrorMsg(cause.getMessage());
      log.error("Failed to execute TRANSFER coordinator for migration {}", execution.getId(), cause);
    }

    if (error != null || isTerminal(execution.getStatus())) {
      try {
        migrationExecutionRepository.save(execution);
      } catch (DataAccessException persistenceError) {
        log.error("Failed to persist TRANSFER result for migration {}", execution.getId(), persistenceError);
        throw persistenceError;
      }
    }
  }

  private Throwable unwrapCompletionException(Throwable error) {
    Throwable cause = error;
    while (cause instanceof CompletionException && cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  private boolean isTerminal(MigrationExecutionStatus status) {
    return status == MigrationExecutionStatus.COMPLETED
      || status == MigrationExecutionStatus.WARNING
      || status == MigrationExecutionStatus.FAILED;
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
    newExecution.setFileTypeCountCompleted(0);
    newExecution.setCreationDate(LocalDateTime.now(ZoneOffset.UTC));
    return newExecution;
  }
}
