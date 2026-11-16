package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.MigrationTransferProperties;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Service
public class MigrationTransferArchiveService {

  private final Path extractorOutputPath;

  public MigrationTransferArchiveService(MigrationTransferProperties transferProperties) {
    this.extractorOutputPath = Path.of(transferProperties.storage().extractorOutputPath())
      .toAbsolutePath()
      .normalize();
  }

  public Resource getDataArchive(MigrationFileExecution fileExecution, MigrationFileType fileType) {
    String filePath = fileExecution.getFilePath();
    if (filePath == null || filePath.isBlank()) {
      throw new NotFoundException(
        "TRANSFER_ARCHIVE_NOT_FOUND",
        "No exported archive is available for migration %s and file type %s"
          .formatted(fileExecution.getMigrationId(), fileType)
      );
    }

    List<String> archives = List.of(filePath.split(",")).stream()
      .map(String::trim)
      .filter(name -> !name.isEmpty())
      .filter(name -> !name.toLowerCase(Locale.ROOT).endsWith(".errors.zip"))
      .toList();
    if (archives.isEmpty()) {
      throw new NotFoundException(
        "TRANSFER_ARCHIVE_NOT_FOUND",
        "No data archive is available for migration %s and file type %s"
          .formatted(fileExecution.getMigrationId(), fileType)
      );
    }
    if (archives.size() != 1) {
      throw new InvalidValueException(
        "TRANSFER_MULTIPLE_ARCHIVES_NOT_SUPPORTED",
        "Expected one data archive for migration %s and file type %s, found %d"
          .formatted(fileExecution.getMigrationId(), fileType, archives.size())
      );
    }

    String extractionId = fileExecution.getExtractionId();
    if (extractionId == null || extractionId.isBlank()
      || extractionId.contains("/") || extractionId.contains("\\")
      || ".".equals(extractionId) || "..".equals(extractionId)) {
      throw new InvalidValueException(
        "TRANSFER_EXTRACTION_ID_INVALID",
        "A valid extraction ID is required to locate the archive for migration %s and file type %s"
          .formatted(fileExecution.getMigrationId(), fileType)
      );
    }

    Path extractionDirectory = extractorOutputPath.resolve(extractionId).normalize();
    Path archivePath = extractionDirectory.resolve(archives.getFirst()).normalize();
    boolean withinExpectedDirectory = extractionDirectory.startsWith(extractorOutputPath)
      && archivePath.startsWith(extractionDirectory);
    boolean regularFile = withinExpectedDirectory && Files.isRegularFile(archivePath);
    boolean readable = withinExpectedDirectory && Files.isReadable(archivePath);
    if (!withinExpectedDirectory || !regularFile || !readable) {
      throw new NotFoundException(
        "TRANSFER_ARCHIVE_NOT_FOUND",
        "Exported archive %s is missing or unreadable".formatted(archives.getFirst())
      );
    }

    // TODO: Decrypt the archive here when encryption is enabled (follow-up PR).
    return new FileSystemResource(archivePath);
  }
}
