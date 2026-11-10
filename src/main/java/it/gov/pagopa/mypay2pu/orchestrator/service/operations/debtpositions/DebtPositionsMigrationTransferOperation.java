package it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositions;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class DebtPositionsMigrationTransferOperation extends AbstractMigrationTransferOperation {

  protected DebtPositionsMigrationTransferOperation() {
    super(MigrationFileType.DEBT_POSITIONS);
  }
}
