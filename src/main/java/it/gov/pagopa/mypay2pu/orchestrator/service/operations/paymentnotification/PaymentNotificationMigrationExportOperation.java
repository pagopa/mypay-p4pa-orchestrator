package it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentnotification;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationExportOperationExecutor;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationExportOperation;
import org.springframework.stereotype.Service;

@Service
public class PaymentNotificationMigrationExportOperation extends AbstractMigrationExportOperation {

  public PaymentNotificationMigrationExportOperation(MigrationExportOperationExecutor operationExecutor) {
    super(operationExecutor);
  }

  @Override
  public MigrationFileType getMigrationFileType() {
    return MigrationFileType.PAYMENT_NOTIFICATION;
  }

}
