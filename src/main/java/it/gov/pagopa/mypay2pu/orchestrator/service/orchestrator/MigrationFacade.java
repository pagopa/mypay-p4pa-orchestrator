package it.gov.pagopa.mypay2pu.orchestrator.service.orchestrator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MigrationFacade {

  private final MigrationOrchestrator orchestrator;

  private final Map<MigrationFileType, MigrationOperation> migrationExportOperations;
  private final Map<MigrationFileType, MigrationOperation> migrationTransferOperations;
  private final Map<MigrationFileType, MigrationOperation> migrationCompleteOperations;

  public MigrationFacade(MigrationOrchestrator orchestrator, ApplicationContext applicationContext) {
    this.orchestrator = orchestrator;

    Map<MigrationFileType, MigrationExportOperation> exportOperations =
      indexOperations(applicationContext.getBeansOfType(MigrationExportOperation.class).values());
    Map<MigrationFileType, MigrationTransferOperation> transferOperations =
      indexOperations(applicationContext.getBeansOfType(MigrationTransferOperation.class).values());

    this.migrationExportOperations = buildSingleOperations(exportOperations);
    this.migrationTransferOperations = buildSingleOperations(transferOperations);
    this.migrationCompleteOperations = buildCompleteOperations(exportOperations, transferOperations);
  }

  public void execute(MigrationRequestDto request) {
    Objects.requireNonNull(request, "request must not be null");
    if (request.getAction() == null) {
      throw new IllegalArgumentException("action must not be null");
    }

    Map<MigrationFileType, MigrationOperation> migrationOperations = switch (request.getAction()) {
      case EXPORT -> migrationExportOperations;
      case TRANSFER -> migrationTransferOperations;
      case COMPLETE -> migrationCompleteOperations;
    };

    orchestrator.execute(request, migrationOperations);
  }

  public Map<MigrationFileType, MigrationOperation> getMigrationExportOperations() {
    return migrationExportOperations;
  }

  public Map<MigrationFileType, MigrationOperation> getMigrationTransferOperations() {
    return migrationTransferOperations;
  }

  public Map<MigrationFileType, MigrationOperation> getMigrationCompleteOperations() {
    return migrationCompleteOperations;
  }

  private static <T extends MigrationOperation> Map<MigrationFileType, T> indexOperations(
    Collection<T> operations
  ) {
    return operations.stream().collect(Collectors.toUnmodifiableMap(
      operation -> Objects.requireNonNull(operation.migrationFileType(), "operation file type must not be null"),
      operation -> operation,
      (first, duplicate) -> {
        throw new IllegalStateException(
          "Duplicate migration operation registered for file type " + first.migrationFileType()
        );
      }
    ));
  }

  private static Map<MigrationFileType, MigrationOperation> buildSingleOperations(
    Map<MigrationFileType, ? extends MigrationOperation> operations
  ) {
    return operations.entrySet().stream().collect(Collectors.toUnmodifiableMap(
      Map.Entry::getKey,
      Map.Entry::getValue
    ));
  }

  private static Map<MigrationFileType, MigrationOperation> buildCompleteOperations(
    Map<MigrationFileType, MigrationExportOperation> exportOperations,
    Map<MigrationFileType, MigrationTransferOperation> transferOperations
  ) {
    return exportOperations.entrySet().stream()
      .filter(entry -> transferOperations.containsKey(entry.getKey()))
      .collect(Collectors.toUnmodifiableMap(
        Map.Entry::getKey,
        entry -> new MigrationCompleteOperation(entry.getValue(), transferOperations.get(entry.getKey()))
      ));
  }
}
