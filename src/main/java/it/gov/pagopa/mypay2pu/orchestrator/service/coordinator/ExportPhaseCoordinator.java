package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFacadeService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Starts export coordination for a persisted migration. */
@Component
public class ExportPhaseCoordinator {

  private final MigrationFacadeService migrationFacadeService;
  private final MigrationExecutionRepository migrationExecutionRepository;

  public ExportPhaseCoordinator(
    MigrationFacadeService migrationFacadeService,
    MigrationExecutionRepository migrationExecutionRepository
  ) {
    this.migrationFacadeService = migrationFacadeService;
    this.migrationExecutionRepository = migrationExecutionRepository;
  }

  @Async("orchestratorTaskExecutor")
  public CompletableFuture<Void> start(MigrationExecution execution) {
    try {
      migrationFacadeService.execute(toRequest(execution));
      execution.setStatus(MigrationExecutionStatus.COMPLETED);
      execution.setFileTypeCountCompleted(execution.getFileTypes().size());
      execution.setErrorCode(null);
      execution.setErrorMsg(null);
      migrationExecutionRepository.save(execution);
    } catch (RuntimeException failure) {
      execution.setStatus(MigrationExecutionStatus.FAILED);
      execution.setErrorCode("EXPORT_FAILED");
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

  private MigrationRequestDto toRequest(MigrationExecution execution) {
    PeriodDto period = execution.getDateFrom() == null && execution.getDateTo() == null
      ? null
      : new PeriodDto(execution.getDateFrom(), execution.getDateTo());

    return new MigrationRequestDto()
      .action(MigrationAction.EXPORT)
      .orchestrationMode(execution.getOrchestrationMode())
      .ipaCodes(List.copyOf(execution.getIpacodes()))
      .fileTypesToInclude(List.copyOf(execution.getFileTypes()))
      .period(period)
      .migrationId(execution.getId())
      .logicalKey(execution.getLogicalKeys());
  }
}
