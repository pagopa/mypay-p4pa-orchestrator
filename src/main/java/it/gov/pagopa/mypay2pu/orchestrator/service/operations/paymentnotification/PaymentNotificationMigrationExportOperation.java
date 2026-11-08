package it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentnotification;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.ExtractorService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionExportService;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationExportOperation;
import org.springframework.stereotype.Service;

@Service
public class PaymentNotificationMigrationExportOperation extends AbstractMigrationExportOperation {

  public PaymentNotificationMigrationExportOperation(
    ExtractorService extractorService,
    MigrationFileExecutionExportService migrationFileExecutionExportService,
    ExtractionPollingProperties pollingProperties
  ) {
    super(
      MigrationFileType.PAYMENT_NOTIFICATION,
      extractorService,
      migrationFileExecutionExportService,
      pollingProperties
    );
  }

}
