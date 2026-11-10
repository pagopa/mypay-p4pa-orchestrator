package it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentsreporting;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class PaymentsReportingMyPivotMigrationTransferOperation extends AbstractMigrationTransferOperation {

  protected PaymentsReportingMyPivotMigrationTransferOperation() {
    super(MigrationFileType.PAYMENTS_REPORTING_MYPIVOT);
  }
}
