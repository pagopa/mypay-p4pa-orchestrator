package it.gov.pagopa.mypay2pu.orchestrator.service.orchestrator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;

public class MigrationExportOperationImpl implements MigrationExportOperation {

  private final MigrationFileType migrationFileType;

  public MigrationExportOperationImpl(MigrationFileType migrationFileType) {
    this.migrationFileType = migrationFileType;
  }

  @Override
  public MigrationFileType migrationFileType() {
    return migrationFileType;
  }

  @Override
  public void execute(MigrationRequestDto request) {
    throw new UnsupportedOperationException(
      "EXPORT for %s is not implemented: the Extractor request and extraction-status handling belong to the Extract component tasks.".formatted(migrationFileType)
    );
  }
}
