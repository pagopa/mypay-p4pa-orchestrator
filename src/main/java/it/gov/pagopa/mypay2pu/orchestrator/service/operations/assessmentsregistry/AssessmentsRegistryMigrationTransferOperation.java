package it.gov.pagopa.mypay2pu.orchestrator.service.operations.assessmentsregistry;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.PuAuthnService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AssessmentsRegistryMigrationTransferOperation extends AbstractMigrationTransferOperation {

  protected AssessmentsRegistryMigrationTransferOperation(
    MigrationFileExecutionTransferService transferService,
    PuMigrationService puMigrationService,
    PuAuthnService puAuthnService,
    @Value("${orchestrator.storage.extractor-output-path}") String extractorOutputPath,
    @Value("${orchestrator.transfer.broker-ipa-code}") String brokerIpaCode
  ) {
    super(MigrationFileType.ASSESSMENTS_REGISTRY, transferService, puMigrationService, puAuthnService, extractorOutputPath, brokerIpaCode);
  }
}
