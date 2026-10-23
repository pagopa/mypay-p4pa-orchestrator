package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client.PuMigrationFileClient;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PuMigrationServiceImpl implements PuMigrationService {

  private final PuMigrationFileClient puMigrationFileClient;

  public PuMigrationServiceImpl(PuMigrationFileClient puMigrationFileClient) {
    this.puMigrationFileClient = puMigrationFileClient;
  }

  @Override
  public UploadMigrationFileResponseDTO uploadMigrationFile(
    String orgIpaCode,
    MigrationFileTypeEnum migrationFileType,
    Resource file,
    String accessToken
  ) {
    return puMigrationFileClient.uploadMigrationFile(
      orgIpaCode, migrationFileType, file, accessToken
    );
  }

  @Override
  public Uploads getMigrationUpload(String orgIpaCode, Long uploadId, String accessToken) {
    return puMigrationFileClient.getMigrationUpload(
      orgIpaCode, uploadId, accessToken
    );
  }

  @Override
  public List<UploadDetails> getMigrationUploadDetails(String orgIpaCode, Long uploadId, String accessToken) {
    return puMigrationFileClient.getMigrationUploadDetails(
      orgIpaCode, uploadId, accessToken
    );
  }

  @Override
  public Resource getMigrationErrors(String orgIpaCode, Long uploadId, String accessToken) {
    return puMigrationFileClient.getMigrationErrors(
      orgIpaCode, uploadId, accessToken
    );
  }
}
