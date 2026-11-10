package it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentnotification;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class PaymentNotificationMigrationTransferOperation extends AbstractMigrationTransferOperation {

  protected PaymentNotificationMigrationTransferOperation() {
    super(MigrationFileType.PAYMENT_NOTIFICATION);
  }
}
