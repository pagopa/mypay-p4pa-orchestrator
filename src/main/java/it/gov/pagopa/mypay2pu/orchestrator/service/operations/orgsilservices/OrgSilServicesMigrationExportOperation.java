package it.gov.pagopa.mypay2pu.orchestrator.service.operations.orgsilservices;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationExportOperationExecutor;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationExportOperation;
import org.springframework.stereotype.Service;

@Service
public class OrgSilServicesMigrationExportOperation extends AbstractMigrationExportOperation {

  public OrgSilServicesMigrationExportOperation(MigrationExportOperationExecutor operationExecutor) {
    super(operationExecutor);
  }

  @Override
  public MigrationFileType getMigrationFileType() {
    return MigrationFileType.ORG_SIL_SERVICES;
  }

}
