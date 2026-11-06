package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationExportOperationExecutor;

public abstract class AbstractMigrationExportOperation implements MigrationExportOperation {

  private final MigrationExportOperationExecutor operationExecutor;

  protected AbstractMigrationExportOperation(MigrationExportOperationExecutor operationExecutor) {
    this.operationExecutor = operationExecutor;
  }

  @Override
  public final void execute(MigrationRequestDto request) {
    operationExecutor.execute(request, getMigrationFileType());
  }
}
