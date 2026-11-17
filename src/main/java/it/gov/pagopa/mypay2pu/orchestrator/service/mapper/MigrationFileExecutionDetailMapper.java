package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
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
      .fileSize(fileSize)
      .status(MigrationFileExecutionDetailStatus.EXPORTED)
      .build();
  }
}
