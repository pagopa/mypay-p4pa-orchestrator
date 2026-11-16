package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import org.springframework.core.io.Resource;

import java.util.List;

public interface PuMigrationService {

  UploadMigrationFileResponseDTO uploadMigrationFile(
    MigrationFileType migrationFileType,
    Resource file
  );

  Uploads getMigrationUpload(Long uploadId);

  List<UploadDetails> getMigrationUploadDetails(Long uploadId);

  Resource getMigrationErrors(Long uploadId);
}
