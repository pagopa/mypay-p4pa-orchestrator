package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApisHolder;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PuMigrationFileClient {

    private final PuMigrationApisHolder puMigrationApisHolder;

    public PuMigrationFileClient(PuMigrationApisHolder puMigrationApisHolder) {
        this.puMigrationApisHolder = puMigrationApisHolder;
    }

    public UploadMigrationFileResponseDTO uploadMigrationFile(
      String orgIpaCode,
      MigrationFileTypeEnum migrationFileType,
      Resource file,
      String accessToken
    ) {
        return puMigrationApisHolder.getMigrationFileApi(accessToken)
                .uploadMigrationFile(orgIpaCode, migrationFileType, file);
    }

    public Uploads getMigrationUpload(String orgIpaCode, Long uploadId, String accessToken) {
        return puMigrationApisHolder.getMigrationFileApi(accessToken)
                .getMigrationUpload(orgIpaCode, uploadId);
    }

    public List<UploadDetails> getMigrationUploadDetails(String orgIpaCode, Long uploadId, String accessToken) {
        return puMigrationApisHolder.getMigrationFileApi(accessToken)
                .getMigrationUploadDetails(orgIpaCode, uploadId);
    }

    public Resource getMigrationErrors(String orgIpaCode, Long uploadId, String accessToken) {
        return puMigrationApisHolder.getMigrationFileApi(accessToken)
                .getMigrationErrors(orgIpaCode, uploadId);
    }

}
