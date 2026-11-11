package it.gov.pagopa.mypay2pu.orchestrator.service.operations.orgsilservices;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.MigrationTransferProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.PuAuthnService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class OrgSilServicesMigrationTransferOperation extends AbstractMigrationTransferOperation {

  public OrgSilServicesMigrationTransferOperation(
    MigrationFileExecutionTransferService transferService,
    PuMigrationService puMigrationService,
    PuAuthnService puAuthnService,
    MigrationTransferProperties transferProperties
  ) {
    super(MigrationFileType.ORG_SIL_SERVICES, transferService, puMigrationService, puAuthnService, transferProperties);
  }
}
