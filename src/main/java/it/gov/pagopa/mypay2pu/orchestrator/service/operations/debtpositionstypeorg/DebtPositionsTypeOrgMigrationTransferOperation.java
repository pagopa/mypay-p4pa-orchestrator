package it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstypeorg;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationToolkitSharedFolderService;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class DebtPositionsTypeOrgMigrationTransferOperation extends AbstractMigrationTransferOperation {

  public DebtPositionsTypeOrgMigrationTransferOperation(
    MigrationFileExecutionTransferService transferService,
    MigrationToolkitSharedFolderService archiveService,
    PuMigrationService puMigrationService
  ) {
    super(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, transferService, archiveService, puMigrationService);
  }
}
