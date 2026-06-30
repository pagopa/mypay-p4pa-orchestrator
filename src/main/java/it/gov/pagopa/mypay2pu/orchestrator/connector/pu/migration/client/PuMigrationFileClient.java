package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApisHolder;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Service
public class PuMigrationFileClient {

    private final PuMigrationApisHolder puMigrationApisHolder;

    public PuMigrationFileClient(PuMigrationApisHolder puMigrationApisHolder) {
        this.puMigrationApisHolder = puMigrationApisHolder;
    }

    public UploadMigrationFileResponseDTO uploadMigrationFile(String orgIpaCode, MigrationFileTypeEnum migrationFileType, String fileName, byte[] fileContent, String accessToken) {
      ByteArrayResource file = new ByteArrayResource(fileContent){
        @Override
        public String getFilename() {
          return fileName;
        }
      };
        return puMigrationApisHolder.getMigrationFileApi(accessToken)
                .uploadMigrationFile(orgIpaCode, migrationFileType, file);
    }

}
