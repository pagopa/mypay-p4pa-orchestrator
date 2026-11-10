package it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionspaid;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class DebtPositionsPaidMigrationTransferOperation extends AbstractMigrationTransferOperation {

  protected DebtPositionsPaidMigrationTransferOperation() {
    super(MigrationFileType.DEBT_POSITIONS_PAID);
  }
}
