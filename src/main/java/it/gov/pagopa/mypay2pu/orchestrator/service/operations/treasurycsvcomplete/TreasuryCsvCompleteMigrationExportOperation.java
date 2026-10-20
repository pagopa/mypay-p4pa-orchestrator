package it.gov.pagopa.mypay2pu.orchestrator.service.operations.treasurycsvcomplete;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationExportOperation;
import org.springframework.stereotype.Service;

@Service
public class TreasuryCsvCompleteMigrationExportOperation implements MigrationExportOperation {

  @Override
  public MigrationFileType migrationFileType() {
    return MigrationFileType.TREASURY_CSV_COMPLETE;
  }

  @Override
  public void execute(MigrationRequestDto request) {
    throw new UnsupportedOperationException(
      "EXPORT for %s is not implemented: the Extractor request and extraction-status handling belong to the Extract component tasks."
        .formatted(migrationFileType())
    );
  }
}
