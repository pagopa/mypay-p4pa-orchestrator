package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;

public interface MigrationOperation {
  MigrationFileType migrationFileType();
  void execute(MigrationRequestDto request);
}
