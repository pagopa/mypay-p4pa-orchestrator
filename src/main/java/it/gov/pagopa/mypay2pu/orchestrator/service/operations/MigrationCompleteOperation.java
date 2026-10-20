package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;

public class MigrationCompleteOperation implements MigrationOperation {

  private final MigrationExportOperation exportOperation;
  private final MigrationTransferOperation transferOperation;

  public MigrationCompleteOperation(MigrationExportOperation exportOperation, MigrationTransferOperation transferOperation) {
    this.exportOperation = exportOperation;
    this.transferOperation = transferOperation;

    MigrationFileType exportFileType = exportOperation.migrationFileType();
    MigrationFileType transferFileType = transferOperation.migrationFileType();
    if (exportFileType != transferFileType) {
      throw new IllegalArgumentException(
        "COMPLETE operations must target the same migration file type: export=%s, transfer=%s".formatted(exportFileType, transferFileType)
      );
    }
  }

  @Override
  public MigrationFileType migrationFileType() {
    return exportOperation.migrationFileType();
  }

  @Override
  public void execute(MigrationRequestDto request) {
    exportOperation.execute(request);
    transferOperation.execute(request);
  }
}
