package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MigrationFileExecutionDetailMapper {

  public List<MigrationFileExecutionDetail> toMigrationFileExecutionDetails(
    UUID migrationFileExecutionId,
    Map<String, Long> fileSizes
  ) {
    return fileSizes.entrySet().stream()
      .map(entry -> toMigrationFileExecutionDetail(
        migrationFileExecutionId, entry.getKey(), entry.getValue()
      ))
      .toList();
  }

  public MigrationFileExecutionDetail toMigrationFileExecutionDetail(
    UUID migrationFileExecutionId,
    String fileName,
    long fileSize
  ) {
    if (StringUtils.isBlank(fileName) || fileSize < 0) {
      throw new InvalidValueException(
        "EXTRACTOR_INVALID_FILE_DETAILS",
        "Invalid extracted file metadata for file %s".formatted(fileName)
      );
    }

    return MigrationFileExecutionDetail.builder()
      .migrationFileExecutionId(migrationFileExecutionId)
      .fileName(fileName)
      .ipaCode(extractIpaCode(fileName))
      .fileSize(fileSize)
      .status(MigrationFileExecutionDetailStatus.EXPORTED)
      .build();
  }

  private String extractIpaCode(String fileName) {
    String baseName = fileName.substring(fileName.lastIndexOf('/') + 1);
    int separator = baseName.indexOf('-');
    if (separator <= 0 || separator > 50 || separator == baseName.length() - 1
      || StringUtils.isBlank(baseName.substring(0, separator))) {
      throw new InvalidValueException(
        "EXTRACTOR_INVALID_FILE_DETAILS",
        "Cannot determine IPA code from extracted file %s".formatted(fileName)
      );
    }
    return baseName.substring(0, separator);
  }

  /** Updates PU fields after the evidence validator has checked the complete snapshot. */
  public void updateMigrationFileExecutionDetail(
    MigrationFileExecutionDetail target,
    UploadDetails uploadDetail
  ) {
    Integer total = uploadDetail.getNumTotalRows() == null ? null : Math.toIntExact(uploadDetail.getNumTotalRows());
    Integer accepted = uploadDetail.getNumCorrectlyImportedRows() == null
      ? null : Math.toIntExact(uploadDetail.getNumCorrectlyImportedRows());
    MigrationFileExecutionDetailStatus status = toLocalStatus(uploadDetail.getStatus());
    target.setUploadDetailId(uploadDetail.getUploadDetailId());
    target.setStatus(status);
    target.setErrorDescription(uploadDetail.getErrorDescription());
    target.setDiscardFileName(uploadDetail.getDiscardFileName());
    target.setNumTotalRows(total);
    target.setNumCorrectlyImportedRows(accepted);
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
