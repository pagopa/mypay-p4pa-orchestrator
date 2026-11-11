package it.gov.pagopa.mypay2pu.orchestrator.service.operations.assessmentsregistry;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.MigrationTransferProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.PuAuthnService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class AssessmentsRegistryMigrationTransferOperation extends AbstractMigrationTransferOperation {

  public AssessmentsRegistryMigrationTransferOperation(
    MigrationFileExecutionTransferService transferService,
    PuMigrationService puMigrationService,
    PuAuthnService puAuthnService,
    MigrationTransferProperties transferProperties
  ) {
    super(MigrationFileType.ASSESSMENTS_REGISTRY, transferService, puMigrationService, puAuthnService, transferProperties);
  }
}
