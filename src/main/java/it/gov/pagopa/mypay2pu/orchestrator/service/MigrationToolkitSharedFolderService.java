package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.MigrationTransferProperties;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class MigrationToolkitSharedFolderService {

  private final Path extractorOutputPath;

  public MigrationToolkitSharedFolderService(MigrationTransferProperties transferProperties) {
    this.extractorOutputPath = Path.of(transferProperties.storage().extractorOutputPath())
      .toAbsolutePath()
      .normalize();
    if (!Files.isDirectory(extractorOutputPath)) {
      throw new InvalidValueException(
        "TRANSFER_SHARED_FOLDER_INVALID",
        "Extractor output path %s does not exist or is not a directory".formatted(extractorOutputPath)
      );
    }
  }

  public Resource getDataArchive(MigrationFileExecution fileExecution, MigrationFileType fileType) {
    String filePath = fileExecution.getFilePath();
    if (StringUtils.isBlank(filePath)) {
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

    return new FileSystemResource(resolveArchive(fileExecution, archives.getFirst()));
  }

  public Map<String, Long> getExportedFileSizes(
    MigrationFileExecution fileExecution,
    List<ArchiveFile> archives
  ) {
    Map<String, Long> fileSizes = new LinkedHashMap<>();
    for (ArchiveFile archive : archives) {
      if (archive.getName().trim().toLowerCase(Locale.ROOT).endsWith(".errors.zip")) {
        continue;
      }
      Path archivePath = resolveArchive(fileExecution, archive.getName().trim());
      try (ZipFile zip = new ZipFile(archivePath.toFile())) {
        collectArchiveFileSizes(zip, archive.getName(), archive.getFiles(), fileSizes);
      } catch (IOException failure) {
        throw new InvalidValueException(
          "EXTRACTOR_ARCHIVE_UNREADABLE",
          "Cannot read extracted archive %s: %s".formatted(archive.getName(), failure.getMessage())
        );
      }
    }
    return fileSizes;
  }

  private void collectArchiveFileSizes(
    ZipFile zip, String archiveName, List<String> files, Map<String, Long> fileSizes
  ) {
    if (files == null) {
      throw new InvalidValueException("EXTRACTOR_INVALID_FILE_DETAILS", "Extractor returned no file list");
    }
    List<String> entries = zip.stream().filter(entry -> !entry.isDirectory()).map(ZipEntry::getName).toList();
    if (entries.size() != files.size() || !files.containsAll(entries)) {
      throw new InvalidValueException(
        "EXTRACTOR_INVALID_FILE_DETAILS",
        "Extractor file list does not match archive %s".formatted(archiveName)
      );
    }
    for (String fileName : files) {
      ZipEntry entry = StringUtils.isBlank(fileName) ? null : zip.getEntry(fileName);
      if (entry == null || entry.isDirectory() || entry.getSize() < 0) {
        throw new InvalidValueException(
          "EXTRACTOR_INVALID_FILE_DETAILS",
          "Extracted file %s is missing or invalid in archive %s".formatted(fileName, archiveName)
        );
      }
      if (fileSizes.putIfAbsent(fileName, entry.getSize()) != null) {
        throw new InvalidValueException(
          "EXTRACTOR_INVALID_FILE_DETAILS", "Duplicate extracted file %s".formatted(fileName)
        );
      }
    }
  }

  private Path resolveArchive(MigrationFileExecution fileExecution, String archiveName) {
    MigrationFileType fileType = fileExecution.getFileType();
    String extractionId = fileExecution.getExtractionId();
    if (StringUtils.isBlank(extractionId)
      || extractionId.contains("/") || extractionId.contains("\\")
      || ".".equals(extractionId) || "..".equals(extractionId)) {
      throw new InvalidValueException(
        "TRANSFER_EXTRACTION_ID_INVALID",
        "A valid extraction ID is required to locate the archive for migration %s and file type %s"
          .formatted(fileExecution.getMigrationId(), fileType)
      );
    }

    Path extractionDirectory = extractorOutputPath.resolve(extractionId).normalize();
    Path archivePath = extractionDirectory.resolve(archiveName).normalize();
    boolean withinExpectedDirectory = extractionDirectory.startsWith(extractorOutputPath)
      && archivePath.startsWith(extractionDirectory);
    boolean regularFile = withinExpectedDirectory && Files.isRegularFile(archivePath);
    boolean readable = withinExpectedDirectory && Files.isReadable(archivePath);
    if (!withinExpectedDirectory || !regularFile || !readable) {
      throw new NotFoundException(
        "TRANSFER_ARCHIVE_NOT_FOUND",
        "Exported archive %s is missing or unreadable".formatted(archiveName)
      );
    }

    // TODO: Decrypt the archive here when encryption is enabled (follow-up PR).
    return archivePath;
  }
}
