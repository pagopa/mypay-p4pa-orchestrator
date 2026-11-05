package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
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

  private final MigrationActionExecutor migrationActionExecutor;
  private final MigrationRequestValidator migrationRequestValidator;
  private final MigrationExecutionRepository migrationExecutionRepository;
  private final MigrationExecutionMapper migrationExecutionMapper;
  private final MigrationResponseMapper migrationResponseMapper;

  public OrchestrateMigrationServiceImpl(
    MigrationActionExecutor migrationActionExecutor,
    MigrationRequestValidator migrationRequestValidator,
    MigrationExecutionRepository migrationExecutionRepository,
    MigrationExecutionMapper migrationExecutionMapper,
    MigrationResponseMapper migrationResponseMapper
  ) {
    this.migrationActionExecutor = migrationActionExecutor;
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
    MigrationResponseDto response = migrationResponseMapper.toMigrationResponseDto(savedExecution);

    migrationActionExecutor.execute(
      migrationExecutionMapper.toMigrationRequestWithMigrationExecutionModelDto(request, savedExecution)
    ).whenComplete((ignored, error) -> {
      if (error != null) {
        log.error("{} action failed for migration {}", action, migrationId, error);
      }
    });

    return response;
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
