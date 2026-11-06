package it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentsreporting;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationExportOperationExecutor;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationExportOperation;
import org.springframework.stereotype.Service;

@Service
public class PaymentsReportingMyPayMigrationExportOperation extends AbstractMigrationExportOperation {

  protected PaymentsReportingMyPayMigrationExportOperation(MigrationExportOperationExecutor operationExecutor) {
    super(operationExecutor);
  }

  @Override
  public MigrationFileType getMigrationFileType() {
    return MigrationFileType.PAYMENTS_REPORTING_MYPAY;
  }

}
