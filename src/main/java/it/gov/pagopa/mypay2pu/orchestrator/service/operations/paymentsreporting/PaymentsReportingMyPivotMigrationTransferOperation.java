package it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentsreporting;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationToolkitSharedFolderService;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class PaymentsReportingMyPivotMigrationTransferOperation extends AbstractMigrationTransferOperation {

  public PaymentsReportingMyPivotMigrationTransferOperation(
    MigrationFileExecutionTransferService transferService,
    MigrationToolkitSharedFolderService archiveService,
    PuMigrationService puMigrationService
  ) {
    super(MigrationFileType.PAYMENTS_REPORTING_MYPIVOT, transferService, archiveService, puMigrationService);
  }
}
