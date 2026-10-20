package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationCompleteOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationTransferOperation;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MigrationFacadeService {

  private final MigrationPlanExecutorService planExecutor;

  private final Map<MigrationFileType, MigrationExportOperation> migrationExportOperations;
  private final Map<MigrationFileType, MigrationTransferOperation> migrationTransferOperations;
  private final Map<MigrationFileType, MigrationOperation> migrationCompleteOperations;

  public MigrationFacadeService(MigrationPlanExecutorService planExecutor, ApplicationContext applicationContext) {
    this.planExecutor = planExecutor;

    this.migrationExportOperations = applicationContext.getBeansOfType(MigrationExportOperation.class).values()
      .stream()
      .collect(Collectors.toUnmodifiableMap(
        MigrationOperation::getMigrationFileType,
        Function.identity()
      ));
    this.migrationTransferOperations = applicationContext.getBeansOfType(MigrationTransferOperation.class).values()
      .stream()
      .collect(Collectors.toUnmodifiableMap(
        MigrationOperation::getMigrationFileType,
        Function.identity()
      ));

    this.migrationCompleteOperations = migrationExportOperations.entrySet().stream()
      .filter(entry -> migrationTransferOperations.containsKey(entry.getKey()))
      .collect(Collectors.toUnmodifiableMap(
        Map.Entry::getKey,
        entry -> new MigrationCompleteOperation(
          entry.getValue(),
          migrationTransferOperations.get(entry.getKey())
        )
      ));
  }

  public void execute(MigrationRequestDto request) {
    Map<MigrationFileType, ? extends MigrationOperation> migrationOperations = switch (request.getAction()) {
      case EXPORT -> migrationExportOperations;
      case TRANSFER -> migrationTransferOperations;
      case COMPLETE -> migrationCompleteOperations;
    };

    planExecutor.execute(request, migrationOperations);
  }

}
