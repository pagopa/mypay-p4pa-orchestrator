package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.config.MigrationTransferProperties;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationToolkitSharedFolderServiceTest {

  @TempDir
  private Path sharedFolder;

  @Test
  void givenExistingDirectoryWhenConstructThenSucceeds() {
    MigrationTransferProperties properties = properties(sharedFolder);

    assertDoesNotThrow(() -> new MigrationToolkitSharedFolderService(properties));
  }

  @Test
  void givenMissingDirectoryWhenConstructThenThrowsInvalidValueException() {
    Path missingDirectory = sharedFolder.resolve("missing");
    MigrationTransferProperties properties = properties(missingDirectory);

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> new MigrationToolkitSharedFolderService(properties)
    );

    assertEquals("TRANSFER_SHARED_FOLDER_INVALID", exception.getCode());
    assertFalse(Files.exists(missingDirectory));
  }

  @Test
  void givenRegularFileWhenConstructThenThrowsInvalidValueException() throws IOException {
    Path file = Files.createFile(sharedFolder.resolve("file.zip"));
    MigrationTransferProperties properties = properties(file);

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> new MigrationToolkitSharedFolderService(properties)
    );

    assertEquals("TRANSFER_SHARED_FOLDER_INVALID", exception.getCode());
  }

  @Test
  void givenDataAndErrorsArchivesWhenGetExportedFileSizesThenReadsOnlyDataEntries() throws IOException {
    MigrationToolkitSharedFolderService service = new MigrationToolkitSharedFolderService(properties(sharedFolder));
    MigrationFileExecution execution = execution("extract-123");
    createZip("extract-123", "data.zip", List.of("part-1.csv", "part-2.csv"));

    Map<String, Long> result = service.getExportedFileSizes(execution, List.of(
      new ArchiveFile("data.zip", List.of("part-1.csv", "part-2.csv")),
      new ArchiveFile("data.errors.zip", List.of("errors.csv"))
    ));

    assertEquals(Map.of("part-1.csv", 3L, "part-2.csv", 3L), result);
  }

  @Test
  void givenMultipleDataArchivesWhenGetExportedFileSizesThenCollectsFilesFromAllArchives() throws IOException {
    MigrationToolkitSharedFolderService service = new MigrationToolkitSharedFolderService(properties(sharedFolder));
    MigrationFileExecution execution = execution("extract-123");
    createZip("extract-123", "first.zip", List.of("part-1.csv"));
    createZip("extract-123", "second.zip", List.of("part-2.csv"));

    Map<String, Long> result = service.getExportedFileSizes(execution, List.of(
      new ArchiveFile("first.zip", List.of("part-1.csv")),
      new ArchiveFile("second.zip", List.of("part-2.csv"))
    ));

    assertEquals(Map.of("part-1.csv", 3L, "part-2.csv", 3L), result);
  }

  @Test
  void givenDuplicateFileNamesWhenGetExportedFileSizesThenRejectsAmbiguousDetails() throws IOException {
    MigrationToolkitSharedFolderService service = new MigrationToolkitSharedFolderService(properties(sharedFolder));
    MigrationFileExecution execution = execution("extract-123");
    createZip("extract-123", "data.zip", List.of("part.csv"));
    createZip("extract-123", "other.zip", List.of("part.csv"));
    List<ArchiveFile> archives = List.of(
      new ArchiveFile("data.zip", List.of("part.csv")),
      new ArchiveFile("other.zip", List.of("part.csv"))
    );

    InvalidValueException exception = assertThrows(InvalidValueException.class,
      () -> service.getExportedFileSizes(execution, archives));

    assertEquals("EXTRACTOR_INVALID_FILE_DETAILS", exception.getCode());
  }

  @Test
  void givenIncompleteFileListWhenGetExportedFileSizesThenRejectsMissingDetails() throws IOException {
    MigrationToolkitSharedFolderService service = new MigrationToolkitSharedFolderService(properties(sharedFolder));
    MigrationFileExecution execution = execution("extract-123");
    createZip("extract-123", "data.zip", List.of("part-1.csv", "part-2.csv"));
    List<ArchiveFile> archives = List.of(new ArchiveFile("data.zip", List.of("part-1.csv")));

    InvalidValueException exception = assertThrows(InvalidValueException.class,
      () -> service.getExportedFileSizes(execution, archives));

    assertEquals("EXTRACTOR_INVALID_FILE_DETAILS", exception.getCode());
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("invalidDeclaredFiles")
  void givenInvalidDeclaredFilesWhenGetExportedFileSizesThenRejectsMetadata(List<String> files) throws IOException {
    MigrationToolkitSharedFolderService service = new MigrationToolkitSharedFolderService(properties(sharedFolder));
    MigrationFileExecution execution = execution("extract-123");
    createZip("extract-123", "data.zip", List.of("part.csv"));
    List<ArchiveFile> archives = List.of(new ArchiveFile("data.zip", files));

    InvalidValueException exception = assertThrows(InvalidValueException.class,
      () -> service.getExportedFileSizes(execution, archives));

    assertEquals("EXTRACTOR_INVALID_FILE_DETAILS", exception.getCode());
  }

  @Test
  void givenDirectoryAndEmptyFileWhenGetExportedFileSizesThenMapsOnlyFileWithZeroSize() throws IOException {
    MigrationToolkitSharedFolderService service = new MigrationToolkitSharedFolderService(properties(sharedFolder));
    MigrationFileExecution execution = execution("extract-123");
    Path directory = Files.createDirectories(sharedFolder.resolve("extract-123"));
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(directory.resolve("data.zip")))) {
      zip.putNextEntry(new ZipEntry("nested/"));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("nested/empty.csv"));
      zip.closeEntry();
    }

    Map<String, Long> result = service.getExportedFileSizes(execution, List.of(
      new ArchiveFile("data.zip", List.of("nested/empty.csv"))
    ));

    assertEquals(Map.of("nested/empty.csv", 0L), result);
  }

  @Test
  void givenInvalidZipWhenGetExportedFileSizesThenReportsReadFailure() throws IOException {
    MigrationToolkitSharedFolderService service = new MigrationToolkitSharedFolderService(properties(sharedFolder));
    MigrationFileExecution execution = execution("extract-123");
    Path directory = Files.createDirectories(sharedFolder.resolve("extract-123"));
    Files.writeString(directory.resolve("data.zip"), "not a zip");
    List<ArchiveFile> archives = List.of(new ArchiveFile("data.zip", List.of("part.csv")));

    InvalidValueException exception = assertThrows(InvalidValueException.class,
      () -> service.getExportedFileSizes(execution, archives));

    assertEquals("EXTRACTOR_ARCHIVE_UNREADABLE", exception.getCode());
    assertTrue(exception.getMessage().startsWith("Cannot read extracted archive data.zip: "));
  }

  @Test
  void givenOutsideArchiveWhenGetExportedFileSizesThenRejectsPath() {
    MigrationToolkitSharedFolderService service = new MigrationToolkitSharedFolderService(properties(sharedFolder));
    MigrationFileExecution execution = execution("extract-123");
    List<ArchiveFile> archives = List.of(new ArchiveFile("../data.zip", List.of("part.csv")));

    assertThrows(it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException.class,
      () -> service.getExportedFileSizes(execution, archives));
  }

  private MigrationFileExecution execution(String extractionId) {
    return MigrationFileExecution.builder()
      .extractionId(extractionId)
      .fileType(MigrationFileType.ORGANIZATIONS)
      .build();
  }

  private static Stream<List<String>> invalidDeclaredFiles() {
    return Stream.of(List.of("missing.csv"), List.of("part.csv", "part.csv"), List.of(" "));
  }

  private void createZip(String extractionId, String name, List<String> files) throws IOException {
    Path directory = Files.createDirectories(sharedFolder.resolve(extractionId));
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(directory.resolve(name)))) {
      for (String file : files) {
        zip.putNextEntry(new ZipEntry(file));
        zip.write(new byte[]{1, 2, 3});
        zip.closeEntry();
      }
    }
  }

  private MigrationTransferProperties properties(Path outputPath) {
    return new MigrationTransferProperties(
      new MigrationTransferProperties.Storage(outputPath.toString())
    );
  }
}
