package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFacadeService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationPlanExecutorService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Coordinates the transfer phase of a migration.
 *
 * <p>Each selected file is submitted only after its selected ancestors have reached a terminal
 * outcome on PU. Evidence collection persists those outcomes; independent sibling branches may
 * continue concurrently.
 *
 * <p>A WARNING outcome is considered sufficient to continue the branch, while a FAILED outcome
 * blocks only its descendants.
 */
@Component
@Slf4j
public class TransferPhaseCoordinator {

  private final MigrationFacadeService migrationFacadeService;
  private final MigrationFileExecutionRepository migrationFileExecutionRepository;
  private final MigrationPlanExecutorService migrationPlanExecutorService;
  private final int pollingIntervalSeconds;
  private final int pollingMaxAttempts;

  public TransferPhaseCoordinator(
    MigrationFacadeService migrationFacadeService,
    MigrationFileExecutionRepository migrationFileExecutionRepository,
    MigrationPlanExecutorService migrationPlanExecutorService,
    @Value("${orchestrator.transfer.polling.interval-seconds}") int pollingIntervalSeconds,
    @Value("${orchestrator.transfer.polling.max-attempts}") int pollingMaxAttempts
  ) {
    if (pollingIntervalSeconds <= 0) {
      throw new IllegalArgumentException("Transfer polling interval must be greater than zero");
    }
    if (pollingMaxAttempts <= 0) {
      throw new IllegalArgumentException("Transfer polling max attempts must be greater than zero");
    }
    this.migrationFacadeService = migrationFacadeService;
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
    this.migrationPlanExecutorService = migrationPlanExecutorService;
    this.pollingIntervalSeconds = pollingIntervalSeconds;
    this.pollingMaxAttempts = pollingMaxAttempts;
  }

  /**
   * Starts transfer coordination for an existing migration.
   *
   * <p>The start routine runs on the orchestrator executor. The returned future tracks its
   * completion, not the completion of the migration itself.
   *
   * @param execution persisted migration whose extracted files should be transferred
   * @return a future completed when the transfer startup routine finishes
   */
  @Async("orchestratorTaskExecutor")
  public CompletableFuture<Void> start(MigrationExecution execution) {
    log.info("Starting TRANSFER coordination for migration {}", execution.getId());
    Set<MigrationFileType> selectedFileTypes = Set.copyOf(execution.getFileTypes());
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      executeNode(execution, MigrationPlanExecutorService.PLAN_ROOT, selectedFileTypes, executor).join();
    } catch (CompletionException exception) {
      if (exception.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      if (exception.getCause() instanceof Error cause) {
        throw cause;
      }
      throw exception;
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> executeNode(
    MigrationExecution execution,
    MigrationFileType fileType,
    Set<MigrationFileType> selectedFileTypes,
    ExecutorService executor
  ) {
    if (selectedFileTypes.contains(fileType)
      && transferAndAwaitOutcome(execution, fileType) == MigrationFileExecutionStatus.FAILED) {
      log.warn(
        "Skipping dependent transfer branch for migration {} because parent file type {} failed",
        execution.getId(),
        fileType
      );
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.allOf(migrationPlanExecutorService.getChildren(fileType).stream()
      .map(child -> CompletableFuture.supplyAsync(
        () -> executeNode(execution, child, selectedFileTypes, executor),
        executor
      ).thenCompose(Function.identity()))
      .toArray(CompletableFuture<?>[]::new));
  }

  private MigrationFileExecutionStatus transferAndAwaitOutcome(
    MigrationExecution execution,
    MigrationFileType fileType
  ) {
    MigrationFileExecution fileExecution = findFileExecution(execution.getId(), fileType);
    MigrationFileExecutionStatus status = fileExecution.getStatus();

    if (isTerminal(status)) {
      return status;
    }

    if (status == MigrationFileExecutionStatus.EXPORTED) {
      migrationFacadeService.execute(transferRequest(execution, fileType));
    } else if (status != MigrationFileExecutionStatus.TRANSFERING
      && status != MigrationFileExecutionStatus.WAITING_PU) {
      throw new IllegalStateException(
        "File type %s for migration %s is not ready for transfer (status=%s)"
          .formatted(fileType, execution.getId(), status)
      );
    }

    return awaitTerminalOutcome(execution.getId(), fileType);
  }

  private MigrationFileExecutionStatus awaitTerminalOutcome(
    UUID migrationId,
    MigrationFileType fileType
  ) {
    for (int attempt = 1; attempt <= pollingMaxAttempts; attempt++) {
      MigrationFileExecutionStatus status = findFileExecution(migrationId, fileType).getStatus();
      if (isTerminal(status)) {
        return status;
      }
      if (attempt < pollingMaxAttempts) {
        waitBeforeNextPoll(migrationId, fileType);
      }
    }
    throw new IllegalStateException(
      "Timed out waiting for PU completion of file type %s for migration %s after %d attempts"
        .formatted(fileType, migrationId, pollingMaxAttempts)
    );
  }

  private void waitBeforeNextPoll(UUID migrationId, MigrationFileType fileType) {
    try {
      Thread.sleep(pollingIntervalSeconds * 1_000L);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
        "Interrupted while waiting for PU completion of file type %s for migration %s"
          .formatted(fileType, migrationId),
        exception
      );
    }
  }

  private MigrationFileExecution findFileExecution(UUID migrationId, MigrationFileType fileType) {
    return migrationFileExecutionRepository.findByMigrationIdAndFileType(migrationId, fileType)
      .orElseThrow(() -> new IllegalStateException(
        "File execution for type %s and migration %s was not found".formatted(fileType, migrationId)
      ));
  }

  private MigrationRequestDto transferRequest(MigrationExecution execution, MigrationFileType fileType) {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setIpaCodes(execution.getIpacodes());
    request.setOrchestrationMode(execution.getOrchestrationMode());
    request.setFileTypesToInclude(List.of(fileType));
    request.setMigrationId(execution.getId());
    return request;
  }

  private boolean isTerminal(MigrationFileExecutionStatus status) {
    return status == MigrationFileExecutionStatus.COMPLETED
      || status == MigrationFileExecutionStatus.WARNING
      || status == MigrationFileExecutionStatus.FAILED;
  }

}
