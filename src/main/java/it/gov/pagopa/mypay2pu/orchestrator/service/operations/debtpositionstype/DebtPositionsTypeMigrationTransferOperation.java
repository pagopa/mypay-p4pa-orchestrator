package it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstype;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class DebtPositionsTypeMigrationTransferOperation implements MigrationTransferOperation {

  @Override
  public MigrationFileType migrationFileType() {
    return MigrationFileType.DEBT_POSITIONS_TYPE;
  }

  @Override
  public void execute(MigrationRequestDto request) {
    throw new UnsupportedOperationException(
      "TRANSFER for %s is not implemented: submitting the extracted file to p4pa-migration and handling its workflow status belong to the Transfer component tasks."
        .formatted(migrationFileType())
    );
  }
}
