package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;

import java.util.Objects;

public abstract class AbstractMigrationTransferOperation implements MigrationTransferOperation {

  private final MigrationFileType fileType;

  protected AbstractMigrationTransferOperation(MigrationFileType fileType) {
    this.fileType = Objects.requireNonNull(fileType, "fileType");
  }

  /**
   * Returns the file type configured for this operation.
   *
   * @return the migration file type
   */
  @Override
  public final MigrationFileType getMigrationFileType() { return fileType; }

  @Override
  public final void execute(MigrationRequestDto request) {
    throw new UnsupportedOperationException(
      "TRANSFER for %s is not implemented: submitting the extracted file to p4pa-migration and handling its workflow status belong to the Transfer component tasks."
        .formatted(getMigrationFileType())
    );
  }
}
