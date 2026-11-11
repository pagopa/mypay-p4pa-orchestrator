package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.BaseBusinessException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferPhaseException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.TransferPhaseCoordinator;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/** Executes a migration action asynchronously and persists its terminal outcome. */
@Component
public class MigrationActionExecutor {

  private final MigrationFacadeService migrationFacadeService;
  private final TransferPhaseCoordinator transferPhaseCoordinator;
  private final MigrationExecutionRepository migrationExecutionRepository;

  public MigrationActionExecutor(
    MigrationFacadeService migrationFacadeService,
    TransferPhaseCoordinator transferPhaseCoordinator,
    MigrationExecutionRepository migrationExecutionRepository
  ) {
    this.migrationFacadeService = migrationFacadeService;
    this.transferPhaseCoordinator = transferPhaseCoordinator;
    this.migrationExecutionRepository = migrationExecutionRepository;
  }

  @Async("orchestratorTaskExecutor")
  public CompletableFuture<Void> execute(MigrationRequestDto request, MigrationExecution execution) {
    // Operations persist file executions under the current execution, not the client-supplied migrationId.
    request.setMigrationId(execution.getId());
    try {
      if (request.getAction() == MigrationAction.TRANSFER) {
        transferPhaseCoordinator.execute(execution);
      } else {
        migrationFacadeService.execute(request);
        execution.setStatus(MigrationExecutionStatus.COMPLETED);
      }
      if (execution.getStatus() != MigrationExecutionStatus.FAILED) {
        execution.setFileTypeCountCompleted(execution.getFileTypes().size());
        execution.setErrorCode(null);
        execution.setErrorMsg(null);
      }
      migrationExecutionRepository.save(execution);
    } catch (RuntimeException failure) {
      execution.setStatus(MigrationExecutionStatus.FAILED);
      execution.setErrorCode(switch (failure) {
        case TransferPhaseException transferPhaseException -> transferPhaseException.getCode();
        case BaseBusinessException businessException -> businessException.getCode();
        case HttpClientErrorException.TooManyRequests tooManyRequestsException -> "TOO_MANY_REQUESTS";
        default -> request.getAction() + "_FAILED";
      });
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
