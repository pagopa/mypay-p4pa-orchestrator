package it.gov.pagopa.mypay2pu.orchestrator.service.tmp;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MigrationFacade {

  private final MigrationOrchestrator orchestrator;

  private final Map<MigrationFileType, MigrationOperation> migrationExportOperations;
  private final Map<MigrationFileType, MigrationOperation> migrationTransferOperations;
  private final Map<MigrationFileType, MigrationOperation> migrationCompleteOperations = new HashMap<>();

  public MigrationFacade(MigrationOrchestrator orchestrator, ApplicationContext applicationContext) {
    this.orchestrator = orchestrator;

    this.migrationExportOperations = applicationContext.getBeansOfType(MigrationExportOperation.class).values()
      .stream()
      .collect(Collectors.toMap(
        MigrationExportOperation::getMigrationFileType,
        Function.identity()
      ));
    this.migrationTransferOperations = applicationContext.getBeansOfType(MigrationTransferOperation.class).values()
      .stream()
      .collect(Collectors.toMap(
        MigrationTransferOperation::getMigrationFileType,
        Function.identity()
      ));

    this.migrationExportOperations.forEach((migrationFileType, exportOperation) ->
      migrationCompleteOperations.put(migrationFileType, new MigrationCompleteOperation((MigrationExportOperation)exportOperation, (MigrationTransferOperation)migrationTransferOperations.get(migrationFileType))));
  }

  void execute(MigrationRequestDto request) {
    Map<MigrationFileType, MigrationOperation> migrationOperations = switch (request.getPhase()) {
      case EXPORT -> migrationExportOperations;
      case TRANSFER -> migrationTransferOperations;
      default -> migrationCompleteOperations;
    };

    orchestrator.execute(request, migrationOperations);
  }
}
