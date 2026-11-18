package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.BaseBusinessException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.EvidenceException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Executes a migration action asynchronously and persists its terminal outcome. */
@Component
public class MigrationActionExecutor {

  private final MigrationFacadeService migrationFacadeService;
  private final MigrationExecutionRepository migrationExecutionRepository;
  private final MigrationFileExecutionRepository migrationFileExecutionRepository;

  public MigrationActionExecutor(MigrationFacadeService migrationFacadeService,
                                 MigrationExecutionRepository migrationExecutionRepository,
                                 MigrationFileExecutionRepository migrationFileExecutionRepository) {
    this.migrationFacadeService = migrationFacadeService;
    this.migrationExecutionRepository = migrationExecutionRepository;
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
  }

  @Async("orchestratorTaskExecutor")
  public CompletableFuture<Void> execute(MigrationRequestDto request, MigrationExecution execution) {
    request.setMigrationId(execution.getId());
    if (execution.getAction() == MigrationAction.TRANSFER) {
      request.setFileTypesToInclude(execution.getFileTypes());
    }
    LocalDateTime previousCheckpoint = execution.getLastSuccessfulExtractionAt();
    try {
      migrationFacadeService.execute(request);
      if (execution.getAction() == MigrationAction.EXPORT) {
        execution.setStatus(MigrationExecutionStatus.COMPLETED);
        execution.setFileTypeCountCompleted(execution.getFileTypes().size());
      } else {
        applyEvidenceOutcome(execution);
      }
      if ((execution.getAction() == MigrationAction.EXPORT || execution.getAction() == MigrationAction.COMPLETE)
        && (execution.getOrchestrationMode() == OrchestrationMode.FULL || execution.getOrchestrationMode() == OrchestrationMode.DELTA)
      ) {
        execution.setLastSuccessfulExtractionAt(execution.getDateTo());
      }
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
      if (execution.getAction() != MigrationAction.EXPORT) {
        try {
          execution.setFileTypeCountCompleted((int) selectedFiles(execution).stream().filter(this::isSuccessfulFile).count());
        } catch (DataAccessException counterFailure) {
          failure.addSuppressed(counterFailure);
        }
      }
      try {
        migrationExecutionRepository.save(execution);
      } catch (RuntimeException persistenceFailure) {
        failure.addSuppressed(persistenceFailure);
      }
      throw failure;
    }
    return CompletableFuture.completedFuture(null);
  }

  private void applyEvidenceOutcome(MigrationExecution execution) {
    List<MigrationFileExecution> selected = selectedFiles(execution);
    execution.setFileTypeCountCompleted((int) selected.stream().filter(this::isSuccessfulFile).count());
    for (MigrationFileExecution file : selected) {
      if (file.getStatus() == MigrationFileExecutionStatus.FAILED) {
        throw new EvidenceException(
          StringUtils.hasText(file.getUploadErrorCode()) ? file.getUploadErrorCode() : "EVIDENCE_PU_FAILED",
          StringUtils.hasText(file.getUploadErrorMsg()) ? file.getUploadErrorMsg() : "PU evidence failed");
      }
    }
    if (selected.size() != execution.getFileTypes().size()
      || selected.stream().map(MigrationFileExecution::getFileType).distinct().count() != selected.size()
      || selected.stream().anyMatch(file -> !isSuccessfulFile(file))) {
      throw new EvidenceException("EVIDENCE_INCOMPLETE", "Migration has missing or unsuccessful PU evidence");
    }
    execution.setStatus(selected.stream().anyMatch(file ->
      file.getStatus() == MigrationFileExecutionStatus.WARNING)
      ? MigrationExecutionStatus.WARNING : MigrationExecutionStatus.COMPLETED);
  }

  private List<MigrationFileExecution> selectedFiles(MigrationExecution execution) {
    return migrationFileExecutionRepository.findAllByMigrationIdOrderByCreationDateAscIdAsc(execution.getId())
      .stream().filter(file -> execution.getFileTypes().contains(file.getFileType())).toList();
  }

  private boolean isSuccessfulFile(MigrationFileExecution file) {
    return file.getStatus() == MigrationFileExecutionStatus.COMPLETED
      || file.getStatus() == MigrationFileExecutionStatus.WARNING;
  }
}
