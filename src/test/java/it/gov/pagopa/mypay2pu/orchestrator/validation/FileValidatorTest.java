package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FileValidatorTest {

  @TempDir private Path tempDir;
  private final FileValidator validator = new FileValidator();

  @Test
  void acceptsRegisteredWorkingDirectory() {
    Path workingDir = tempDir.resolve("work");

    assertDoesNotThrow(() -> validator.requireWorkingDirectory(workingDir, Set.of(workingDir)));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"other", "work/child"})
  void rejectsUnregisteredWorkingDirectory(String candidate) {
    Path workingDir = candidate == null ? null : resolvePath(candidate);
    Set<Path> registeredDirs = Set.of(resolvePath("work"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
      () -> validator.requireWorkingDirectory(workingDir, registeredDirs));

    assertEquals("Working directory was not created by this service", error.getMessage());
  }

  @Test
  void acceptsArchiveWithDistinctEntries() throws IOException {
    Path archive = zip("first.csv", "folder/second.csv");
    Path extracted = resolvePath("extracted");

    assertDoesNotThrow(() -> validator.validateArchive(archive, extracted));
  }

  @Test
  void rejectsEntryOutsideExtractionDirectory() throws IOException {
    Path archive = zip("../outside.csv");
    Path extracted = resolvePath("extracted");

    InvalidFileException error = assertThrows(InvalidFileException.class,
      () -> validator.validateArchive(archive, extracted));

    assertEquals("INVALID_FILE", error.getCode());
    assertTrue(error.getMessage().contains("../outside.csv"));
  }

  @Test
  void rejectsEntriesResolvingToSamePath() throws IOException {
    Path archive = zip("folder/file.csv", "folder/./file.csv");
    Path extracted = resolvePath("extracted");

    InvalidFileException error = assertThrows(InvalidFileException.class,
      () -> validator.validateArchive(archive, extracted));

    assertEquals("INVALID_FILE", error.getCode());
    assertTrue(error.getMessage().contains("folder/./file.csv"));
  }


  @Test
  void rejectsMalformedArchiveAndPreservesCause() throws IOException {
    Path archive = resolvePath("invalid.zip");
    Files.writeString(archive, "is not a zip", StandardCharsets.UTF_8);
    Path extracted = resolvePath("extracted");

    InvalidFileException error = assertThrows(InvalidFileException.class,
      () -> validator.validateArchive(archive, extracted));

    assertEquals("INVALID_FILE", error.getCode());
    assertTrue(error.getMessage().contains(archive.toString()));
    assertInstanceOf(IOException.class, error.getCause());
  }

  private Path zip(String... entryNames) throws IOException {
    Path archive = resolvePath("archive.zip");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
      for (String name : entryNames) {
        output.putNextEntry(new ZipEntry(name));
        output.write("data".getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
      }
    }
    return archive;
  }

  private Path resolvePath(String name) { return tempDir.resolve(name); }
}
