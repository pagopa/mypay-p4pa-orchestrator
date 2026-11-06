package it.gov.pagopa.mypay2pu.orchestrator.service.operations.assessments;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class AssessmentsMigrationTransferOperation extends AbstractMigrationTransferOperation {

  @Override
  public MigrationFileType getMigrationFileType() {
    return MigrationFileType.ASSESSMENTS;
  }
}
