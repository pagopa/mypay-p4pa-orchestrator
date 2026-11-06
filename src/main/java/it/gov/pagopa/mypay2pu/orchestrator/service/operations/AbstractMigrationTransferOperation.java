package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;

public abstract class AbstractMigrationTransferOperation implements MigrationTransferOperation {

  @Override
  public final void execute(MigrationRequestDto request) {
    throw new UnsupportedOperationException(
      "TRANSFER for %s is not implemented: submitting the extracted file to p4pa-migration and handling its workflow status belong to the Transfer component tasks."
        .formatted(getMigrationFileType())
    );
  }
}
