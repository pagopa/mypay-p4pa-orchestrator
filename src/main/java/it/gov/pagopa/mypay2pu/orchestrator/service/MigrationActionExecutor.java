package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.BaseBusinessException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Executes a migration action asynchronously and persists its terminal outcome. */
@Component
public class MigrationActionExecutor {

  private final MigrationFacadeService migrationFacadeService;
  private final MigrationExecutionRepository migrationExecutionRepository;

  public MigrationActionExecutor(MigrationFacadeService migrationFacadeService,
                                 MigrationExecutionRepository migrationExecutionRepository) {
    this.migrationFacadeService = migrationFacadeService;
    this.migrationExecutionRepository = migrationExecutionRepository;
  }

  @Async("orchestratorTaskExecutor")
  public CompletableFuture<Void> execute(MigrationRequestDto request, MigrationExecution execution) {
    request.setMigrationId(execution.getId());
    LocalDateTime previousCheckpoint = execution.getLastSuccessfulExtractionAt();
    try {
      migrationFacadeService.execute(request);
      if ((execution.getAction() == MigrationAction.EXPORT || execution.getAction() == MigrationAction.COMPLETE)
        && (execution.getOrchestrationMode() == OrchestrationMode.FULL || execution.getOrchestrationMode() == OrchestrationMode.DELTA)
      ) {
        execution.setLastSuccessfulExtractionAt(execution.getDateTo());
      }
      execution.setStatus(MigrationExecutionStatus.COMPLETED);
      execution.setFileTypeCountCompleted(execution.getFileTypes().size());
      execution.setErrorCode(null);
      execution.setErrorMsg(null);
      migrationExecutionRepository.save(execution);
    } catch (RuntimeException failure) {
      execution.setLastSuccessfulExtractionAt(previousCheckpoint);
      execution.setStatus(MigrationExecutionStatus.FAILED);
      execution.setErrorCode(failure instanceof BaseBusinessException businessException
        ? businessException.getCode()
        : request.getAction() + "_FAILED");
      execution.setErrorMsg(
        StringUtils.hasText(failure.getMessage()) ? failure.getMessage() : failure.getClass().getSimpleName()
      );
      try {
        migrationExecutionRepository.save(execution);
      } catch (RuntimeException persistenceFailure) {
        failure.addSuppressed(persistenceFailure);
      }
      throw failure;
    }
    return CompletableFuture.completedFuture(null);
  }
}
