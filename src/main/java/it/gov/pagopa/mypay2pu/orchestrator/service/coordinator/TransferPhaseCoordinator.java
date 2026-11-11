package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.BaseBusinessException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferPhaseException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Coordinates the transfer phase of a migration inside the global action executor.
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

  /** Executes the transfer phase for an existing migration. */
  public void execute(MigrationExecution execution) {
    log.info("Starting TRANSFER coordination for migration {}", execution.getId());
    Set<MigrationFileType> selectedFileTypes = Set.copyOf(execution.getFileTypes());
    AtomicBoolean hasWarning = new AtomicBoolean();
    AtomicReference<FailedFile> failure = new AtomicReference<>();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      executeNode(
        execution,
        MigrationPlanExecutorService.PLAN_ROOT,
        selectedFileTypes,
        hasWarning,
        failure,
        executor
      ).join();
    } catch (CompletionException exception) {
      if (exception.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      if (exception.getCause() instanceof Error cause) {
        throw cause;
      }
      throw exception;
    }
    if (failure.get() != null) {
      execution.setStatus(MigrationExecutionStatus.FAILED);
      String errorCode = failure.get().errorCode();
      execution.setErrorCode(errorCode == null || errorCode.isBlank() ? "TRANSFER_FAILED" : errorCode);
      execution.setErrorMsg(
        "Transfer failed because file type %s reached FAILED".formatted(failure.get().fileType())
      );
    } else if (hasWarning.get()) {
      execution.setStatus(MigrationExecutionStatus.WARNING);
      execution.setErrorCode(null);
      execution.setErrorMsg(null);
    } else {
      execution.setStatus(MigrationExecutionStatus.COMPLETED);
      execution.setErrorCode(null);
      execution.setErrorMsg(null);
    }
  }

  private CompletableFuture<Void> executeNode(
    MigrationExecution execution,
    MigrationFileType fileType,
    Set<MigrationFileType> selectedFileTypes,
    AtomicBoolean hasWarning,
    AtomicReference<FailedFile> failure,
    ExecutorService executor
  ) {
    if (selectedFileTypes.contains(fileType)) {
      MigrationFileExecution fileExecution = transferAndAwaitOutcome(execution, fileType);
      MigrationFileExecutionStatus outcome = fileExecution.getStatus();
      if (outcome == MigrationFileExecutionStatus.WARNING) {
        hasWarning.set(true);
      }
      if (outcome == MigrationFileExecutionStatus.FAILED) {
        failure.compareAndSet(
          null,
          new FailedFile(fileType, fileExecution.getUploadErrorCode())
        );
        log.warn(
          "Skipping dependent transfer branch for migration {} because parent file type {} failed",
          execution.getId(),
          fileType
        );
        return CompletableFuture.completedFuture(null);
      }
    }

    return CompletableFuture.allOf(migrationPlanExecutorService.getChildren(fileType).stream()
      .map(child -> CompletableFuture.supplyAsync(
        () -> executeNode(
          execution,
          child,
          selectedFileTypes,
          hasWarning,
          failure,
          executor
        ),
        executor
      ).thenCompose(Function.identity()))
      .toArray(CompletableFuture<?>[]::new));
  }

  private MigrationFileExecution transferAndAwaitOutcome(
    MigrationExecution execution,
    MigrationFileType fileType
  ) {
    MigrationFileExecution fileExecution = findFileExecution(execution.getId(), fileType);
    MigrationFileExecutionStatus status = fileExecution.getStatus();

    try {
      if (isTerminal(status)) {
        return fileExecution;
      }

      if (status == MigrationFileExecutionStatus.EXPORTED) {
        migrationFacadeService.execute(transferRequest(execution, fileType));
      } else if (status != MigrationFileExecutionStatus.TRANSFERING
        && status != MigrationFileExecutionStatus.WAITING_PU) {
        throw new TransferPhaseException(
          "TRANSFER_FILE_NOT_READY",
          "File type %s for migration %s is not ready for transfer (status=%s)"
            .formatted(fileType, execution.getId(), status)
        );
      }

      return awaitTerminalOutcome(execution.getId(), fileType);
    } catch (RuntimeException exception) {
      try {
        markFileExecutionAsFailed(fileExecution, exception);
      } catch (RuntimeException persistenceFailure) {
        exception.addSuppressed(persistenceFailure);
      }
      throw exception;
    }
  }

  private void markFileExecutionAsFailed(
    MigrationFileExecution fileExecution,
    RuntimeException exception
  ) {
    MigrationFileExecution current = findFileExecution(fileExecution.getMigrationId(), fileExecution.getFileType());
    MigrationFileExecutionStatus status = current.getStatus();
    if (status == MigrationFileExecutionStatus.FAILED) {
      return;
    }
    if (status != MigrationFileExecutionStatus.EXPORTED
      && status != MigrationFileExecutionStatus.TRANSFERING
      && status != MigrationFileExecutionStatus.WAITING_PU) {
      return;
    }

    current.setStatus(MigrationFileExecutionStatus.FAILED);
    if (current.getUploadErrorCode() == null || current.getUploadErrorCode().isBlank()) {
      current.setUploadErrorCode(errorCode(exception));
    }
    if (current.getUploadErrorMsg() == null || current.getUploadErrorMsg().isBlank()) {
      current.setUploadErrorMsg(errorMessage(exception));
    }
    migrationFileExecutionRepository.save(current);
  }

  private String errorCode(RuntimeException exception) {
    return switch (exception) {
      case TransferPhaseException transferPhaseException -> transferPhaseException.getCode();
      case BaseBusinessException businessException -> businessException.getCode();
      case HttpClientErrorException.TooManyRequests tooManyRequestsException -> "TOO_MANY_REQUESTS";
      default -> "TRANSFER_FAILED";
    };
  }

  private String errorMessage(RuntimeException exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
      ? exception.getClass().getSimpleName()
      : exception.getMessage();
  }

  private MigrationFileExecution awaitTerminalOutcome(
    UUID migrationId,
    MigrationFileType fileType
  ) {
    for (int attempt = 1; attempt <= pollingMaxAttempts; attempt++) {
      MigrationFileExecution fileExecution = findFileExecution(migrationId, fileType);
      if (isTerminal(fileExecution.getStatus())) {
        return fileExecution;
      }
      if (attempt < pollingMaxAttempts) {
        waitBeforeNextPoll(migrationId, fileType);
      }
    }
    throw new TransferPhaseException(
      "TRANSFER_PU_TIMEOUT",
      "Timed out waiting for PU completion of file type %s for migration %s after %d attempts"
        .formatted(fileType, migrationId, pollingMaxAttempts)
    );
  }

  private void waitBeforeNextPoll(UUID migrationId, MigrationFileType fileType) {
    try {
      Thread.sleep(pollingIntervalSeconds * 1_000L);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new TransferPhaseException(
        "TRANSFER_PU_INTERRUPTED",
        "Interrupted while waiting for PU completion of file type %s for migration %s"
          .formatted(fileType, migrationId),
        exception
      );
    }
  }

  private MigrationFileExecution findFileExecution(UUID migrationId, MigrationFileType fileType) {
    return migrationFileExecutionRepository.findByMigrationIdAndFileType(migrationId, fileType)
      .orElseThrow(() -> new TransferPhaseException(
        "TRANSFER_FILE_EXECUTION_NOT_FOUND",
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

  private record FailedFile(MigrationFileType fileType, String errorCode) { }
}
