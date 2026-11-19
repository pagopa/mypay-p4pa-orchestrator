package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class MigrationFileExecutionDetailMapper {

  public List<MigrationFileExecutionDetail> toMigrationFileExecutionDetails(
    UUID migrationFileExecutionId,
    List<ArchiveFile> archives
  ) {
    List<MigrationFileExecutionDetail> details = new ArrayList<>();
    Set<String> fileNames = new HashSet<>();
    for (ArchiveFile archive : archives) {
      if (archive.getName().trim().toLowerCase(Locale.ROOT).endsWith(".errors.zip")) {
        continue;
      }
      if (archive.getFiles() == null) {
        throw new InvalidValueException("EXTRACTOR_INVALID_FILE_DETAILS", "Extractor returned no file list");
      }
      for (String fileName : archive.getFiles()) {
        MigrationFileExecutionDetail detail =
          toMigrationFileExecutionDetail(migrationFileExecutionId, fileName, 0L);
        if (!fileNames.add(fileName)) {
          throw new InvalidValueException(
            "EXTRACTOR_INVALID_FILE_DETAILS", "Duplicate extracted file %s".formatted(fileName)
          );
        }
        details.add(detail);
      }
    }
    return details;
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
}
