package it.gov.pagopa.mypay2pu.orchestrator.service.tmp;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;

public class MigrationCompleteOperation implements MigrationOperation {

  private final MigrationExportOperation exportOperation;
  private final MigrationTransferOperation transferOperation;

  public MigrationCompleteOperation(MigrationExportOperation exportOperation, MigrationTransferOperation transferOperation) {
    this.exportOperation = exportOperation;
    this.transferOperation = transferOperation;

    if(exportOperation.getMigrationFileType().equals(transferOperation.getMigrationFileType())){
      throw new IllegalStateException("Invalid configuration:" + MigrationExportOperation.class.getSimpleName()  + MigrationTransferOperation.class.getSimpleName());
    }
  }

  @Override
  public MigrationFileType getMigrationFileType() {
    return exportOperation.getMigrationFileType();
  }

  @Override
  public void execute(MigrationRequestDto request) {
    exportOperation.execute(request);
    transferOperation.execute(request);
  }
}
