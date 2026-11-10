package it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstype;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class DebtPositionsTypeMigrationTransferOperation extends AbstractMigrationTransferOperation {

  protected DebtPositionsTypeMigrationTransferOperation() {
    super(MigrationFileType.DEBT_POSITIONS_TYPE);
  }
}
