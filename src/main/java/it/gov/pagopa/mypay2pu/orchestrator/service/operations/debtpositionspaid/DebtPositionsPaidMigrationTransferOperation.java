package it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionspaid;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class DebtPositionsPaidMigrationTransferOperation implements MigrationTransferOperation {

  @Override
  public MigrationFileType migrationFileType() {
    return MigrationFileType.DEBT_POSITIONS_PAID;
  }

  @Override
  public void execute(MigrationRequestDto request) {
    throw new UnsupportedOperationException(
      "TRANSFER for %s is not implemented: submitting the extracted file to p4pa-migration and handling its workflow status belong to the Transfer component tasks."
        .formatted(migrationFileType())
    );
  }
}
