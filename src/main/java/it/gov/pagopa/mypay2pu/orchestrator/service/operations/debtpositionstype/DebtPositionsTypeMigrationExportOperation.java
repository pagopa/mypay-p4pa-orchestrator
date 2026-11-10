package it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstype;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.ExtractorService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionExportService;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationExportOperation;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DebtPositionsTypeMigrationExportOperation extends AbstractMigrationExportOperation {

  public DebtPositionsTypeMigrationExportOperation(ExtractorService extractorService,
                                                   MigrationFileExecutionExportService migrationFileExecutionExportService,
                                                   ExtractionPollingProperties pollingProperties,
                                                   JsonMapper jsonMapper) {
    super(MigrationFileType.DEBT_POSITIONS_TYPE, extractorService, migrationFileExecutionExportService, pollingProperties, jsonMapper);
  }
}
