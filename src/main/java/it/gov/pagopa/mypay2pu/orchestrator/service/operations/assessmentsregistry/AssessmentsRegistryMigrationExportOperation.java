package it.gov.pagopa.mypay2pu.orchestrator.service.operations.assessmentsregistry;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.ExtractorService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionExportService;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationExportOperation;
import org.springframework.stereotype.Service;

@Service
public class AssessmentsRegistryMigrationExportOperation extends AbstractMigrationExportOperation {

  public AssessmentsRegistryMigrationExportOperation(ExtractorService extractorService,
                                                     MigrationFileExecutionExportService migrationFileExecutionExportService,
                                                     ExtractionPollingProperties pollingProperties) {
    super(MigrationFileType.ASSESSMENTS_REGISTRY, extractorService, migrationFileExecutionExportService, pollingProperties);
  }
}
