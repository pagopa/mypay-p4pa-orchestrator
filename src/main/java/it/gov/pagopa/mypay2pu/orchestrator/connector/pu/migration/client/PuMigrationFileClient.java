package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApisHolder;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
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

    // Generated @Nonnull getters can return null for incomplete PU responses.
    @SuppressWarnings("java:S2589")
    public Uploads getMigrationUpload(String orgIpaCode, Long uploadId, String accessToken) {
        Uploads upload = puMigrationApisHolder.getMigrationFileApi(accessToken)
                .getMigrationUpload(orgIpaCode, uploadId);
        if (upload == null || upload.getStatus() == null) {
            throw new InvalidValueException(
                "TRANSFER_OUTCOME_INVALID_RESPONSE",
                "PU returned a missing upload or status for upload " + uploadId
            );
        }
        return upload;
    }

    // Generated @Nonnull getters can return null for incomplete PU responses.
    @SuppressWarnings("java:S2589")
    public List<UploadDetails> getMigrationUploadDetails(String orgIpaCode, Long uploadId, String accessToken) {
        List<UploadDetails> details = puMigrationApisHolder.getMigrationFileApi(accessToken)
                .getMigrationUploadDetails(orgIpaCode, uploadId);
        if (details == null || details.stream().anyMatch(detail ->
            detail == null || detail.getUploadId() == null || detail.getOrganizationId() == null
                || detail.getIngestionFlowFileType() == null || detail.getFilePathName() == null
                || detail.getFileName() == null || detail.getFileSize() == null || detail.getStatus() == null)) {
            throw new InvalidValueException(
                "TRANSFER_OUTCOME_INVALID_RESPONSE",
                "PU returned invalid details for upload " + uploadId
            );
        }
        return details;
    }

    public Resource getMigrationErrors(String orgIpaCode, Long uploadId, String accessToken) {
        return puMigrationApisHolder.getMigrationFileApi(accessToken)
                .getMigrationErrors(orgIpaCode, uploadId);
    }

}
