package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration;

import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import org.springframework.core.io.Resource;

import java.util.List;

public interface PuMigrationService {

  UploadMigrationFileResponseDTO uploadMigrationFile(
    String orgIpaCode,
    MigrationFileTypeEnum migrationFileType,
    Resource file,
    String accessToken
  );

  Uploads getMigrationUpload(String orgIpaCode, Long uploadId, String accessToken);

  List<UploadDetails> getMigrationUploadDetails(String orgIpaCode, Long uploadId, String accessToken);

  Resource getMigrationErrors(String orgIpaCode, Long uploadId, String accessToken);
}
