package it.gov.pagopa.mypay2pu.orchestrator.service.operations.treasurycsvcomplete;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationExportOperationExecutor;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationExportOperation;
import org.springframework.stereotype.Service;

@Service
public class TreasuryCsvCompleteMigrationExportOperation extends AbstractMigrationExportOperation {

  public TreasuryCsvCompleteMigrationExportOperation(MigrationExportOperationExecutor operationExecutor) {
    super(operationExecutor);
  }

  @Override
  public MigrationFileType getMigrationFileType() {
    return MigrationFileType.TREASURY_CSV_COMPLETE;
  }

}
