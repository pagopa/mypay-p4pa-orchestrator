package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.MigrationRequestWithMigrationExecutionModelDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Executes a migration action asynchronously and persists its terminal outcome. */
@Component
public class MigrationActionExecutor {

  private final MigrationFacadeService migrationFacadeService;
  private final MigrationExecutionRepository migrationExecutionRepository;

  public MigrationActionExecutor(
    MigrationFacadeService migrationFacadeService,
    MigrationExecutionRepository migrationExecutionRepository
  ) {
    this.migrationFacadeService = migrationFacadeService;
    this.migrationExecutionRepository = migrationExecutionRepository;
  }

  @Async("orchestratorTaskExecutor")
  public CompletableFuture<Void> execute(MigrationRequestWithMigrationExecutionModelDto request) {
    MigrationExecution execution = request.getMigrationExecution();
    try {
      migrationFacadeService.execute(request.getMigrationRequest());
      execution.setStatus(MigrationExecutionStatus.COMPLETED);
      execution.setFileTypeCountCompleted(execution.getFileTypes().size());
      execution.setErrorCode(null);
      execution.setErrorMsg(null);
      migrationExecutionRepository.save(execution);
    } catch (RuntimeException failure) {
      execution.setStatus(MigrationExecutionStatus.FAILED);
      execution.setErrorCode(request.getMigrationRequest().getAction() + "_FAILED");
      execution.setErrorMsg(
        failure.getMessage() == null || failure.getMessage().isBlank()
          ? failure.getClass().getSimpleName()
          : failure.getMessage()
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
