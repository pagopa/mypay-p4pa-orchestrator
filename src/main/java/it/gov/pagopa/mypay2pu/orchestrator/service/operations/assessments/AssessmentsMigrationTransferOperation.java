package it.gov.pagopa.mypay2pu.orchestrator.service.operations.assessments;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationTransferArchiveService;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class AssessmentsMigrationTransferOperation extends AbstractMigrationTransferOperation {

  public AssessmentsMigrationTransferOperation(
    MigrationFileExecutionTransferService transferService,
    MigrationTransferArchiveService archiveService,
    PuMigrationService puMigrationService
  ) {
    super(MigrationFileType.ASSESSMENTS, transferService, archiveService, puMigrationService);
  }
}
