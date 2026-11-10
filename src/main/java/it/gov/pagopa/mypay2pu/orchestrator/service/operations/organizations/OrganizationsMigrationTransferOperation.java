package it.gov.pagopa.mypay2pu.orchestrator.service.operations.organizations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class OrganizationsMigrationTransferOperation extends AbstractMigrationTransferOperation {

  protected OrganizationsMigrationTransferOperation() {
    super(MigrationFileType.ORGANIZATIONS);
  }
}
