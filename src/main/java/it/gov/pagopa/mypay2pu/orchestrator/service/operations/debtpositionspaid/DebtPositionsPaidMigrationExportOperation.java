package it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionspaid;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationExportOperationExecutor;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationExportOperation;
import org.springframework.stereotype.Service;

@Service
public class DebtPositionsPaidMigrationExportOperation extends AbstractMigrationExportOperation {

  public DebtPositionsPaidMigrationExportOperation(MigrationExportOperationExecutor operationExecutor) {
    super(operationExecutor);
  }

  @Override
  public MigrationFileType getMigrationFileType() {
    return MigrationFileType.DEBT_POSITIONS_PAID;
  }

}
