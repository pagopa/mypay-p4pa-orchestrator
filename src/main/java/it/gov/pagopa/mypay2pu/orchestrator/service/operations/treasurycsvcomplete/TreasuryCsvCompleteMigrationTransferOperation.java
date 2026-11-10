package it.gov.pagopa.mypay2pu.orchestrator.service.operations.treasurycsvcomplete;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class TreasuryCsvCompleteMigrationTransferOperation extends AbstractMigrationTransferOperation {

  protected TreasuryCsvCompleteMigrationTransferOperation() {
    super(MigrationFileType.TREASURY_CSV_COMPLETE);
  }
}
