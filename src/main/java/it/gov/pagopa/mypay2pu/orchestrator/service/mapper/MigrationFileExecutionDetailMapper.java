package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Utilities;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class MigrationFileExecutionDetailMapper {

  public MigrationFileExecutionDetail toMigrationFileExecutionDetail(
    UUID migrationFileExecutionId,
    Long uploadId,
    UploadDetails uploadDetail
  ) {
    if (uploadDetail == null
      || !Objects.equals(uploadDetail.getUploadId(), uploadId)
      || StringUtils.isBlank(uploadDetail.getFileName())
      || uploadDetail.getFileSize() < 0) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "PU returned invalid upload details for upload %s".formatted(uploadId)
      );
    }

    Integer numTotalRows = Utilities.toNonNegativeInteger(uploadDetail.getNumTotalRows(), "numTotalRows");
    Integer numCorrectlyImportedRows =
      Utilities.toNonNegativeInteger(uploadDetail.getNumCorrectlyImportedRows(), "numCorrectlyImportedRows");
    if (numTotalRows != null && numCorrectlyImportedRows != null && numCorrectlyImportedRows > numTotalRows) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "numCorrectlyImportedRows exceeds numTotalRows for upload %s".formatted(uploadId)
      );
    }

    return MigrationFileExecutionDetail.builder()
      .migrationFileExecutionId(migrationFileExecutionId)
      .fileName(uploadDetail.getFileName())
      .uploadDetailId(uploadDetail.getUploadDetailId())
      .fileSize(uploadDetail.getFileSize())
      .status(toLocalStatus(uploadDetail.getStatus()))
      .errorDescription(uploadDetail.getErrorDescription())
      .discardFileName(uploadDetail.getDiscardFileName())
      .numTotalRows(numTotalRows)
      .numCorrectlyImportedRows(numCorrectlyImportedRows)
      .build();
  }

  private MigrationFileExecutionDetailStatus toLocalStatus(IngestionFlowFileStatus status) {
    return switch (status) {
      case UPLOADED, PROCESSING, WAITING_FILE -> MigrationFileExecutionDetailStatus.WAITING_PU;
      case COMPLETED -> MigrationFileExecutionDetailStatus.COMPLETED;
      case WARNING -> MigrationFileExecutionDetailStatus.PARTIAL;
      case ERROR -> MigrationFileExecutionDetailStatus.FAILED;
    };
  }
}
