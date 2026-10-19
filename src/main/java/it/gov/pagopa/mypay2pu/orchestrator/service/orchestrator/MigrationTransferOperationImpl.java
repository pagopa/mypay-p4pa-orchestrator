package it.gov.pagopa.mypay2pu.orchestrator.service.orchestrator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;

public class MigrationTransferOperationImpl implements MigrationTransferOperation {

  private final MigrationFileType migrationFileType;

  public MigrationTransferOperationImpl(MigrationFileType migrationFileType) {
    this.migrationFileType = migrationFileType;
  }

  @Override
  public MigrationFileType migrationFileType() {
    return migrationFileType;
  }

  @Override
  public void execute(MigrationRequestDto request) {
    throw new UnsupportedOperationException(
      "TRANSFER for %s is not implemented: submitting the extracted file to p4pa-migration and handling its workflow status belong to the Transfer component tasks.".formatted(migrationFileType)
    );

  }
}
