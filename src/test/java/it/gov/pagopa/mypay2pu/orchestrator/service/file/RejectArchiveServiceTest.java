package it.gov.pagopa.mypay2pu.orchestrator.service.file;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidFileException;
import it.gov.pagopa.mypay2pu.orchestrator.validation.FileValidator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RejectArchiveServiceTest {

  @TempDir private Path tempDir;
  @Mock private PuMigrationService migrationService;
  private RejectArchiveService service;

  @BeforeEach
  void setUp() {
    service = new RejectArchiveService(migrationService, new ZipFileService(100, 1024 * 1024, 100),
      new FileValidator(),
      tempDir.toString());
  }

  @Test
  void extractsEachUploadInIsolationAndDeletesAllTemporaryFiles() throws IOException {
    when(migrationService.getMigrationErrors(12L)).thenReturn(new ByteArrayResource(zip("errors.csv", "one")));
    when(migrationService.getMigrationErrors(13L)).thenReturn(new ByteArrayResource(zip("errors.csv", "two")));
    Path workingDir = service.createWorkingDirectory("IPA_TEST");
    Path root = workingDir.getParent();
    Path other = service.createWorkingDirectory("IPA_TEST");

    try {
      List<Path> first = service.retrieveAndUnzipFile(12L, workingDir);
      List<Path> second = service.retrieveAndUnzipFile(13L, workingDir);
      assertEquals("IPA_TEST", workingDir.getFileName().toString());
      assertNotEquals(workingDir, other);
      assertNotEquals(first.getFirst(), second.getFirst());
      assertEquals("one", Files.readString(first.getFirst()));
      assertEquals("two", Files.readString(second.getFirst()));
    } finally {
      service.deleteWorkingDirectory(workingDir);
      service.deleteWorkingDirectory(other);
    }

    assertFalse(Files.exists(root));
    assertFalse(Files.exists(other.getParent()));
  }

  @Test
  void rejectsInvalidArchiveAndCleansUpAfterFailure() throws IOException {
    when(migrationService.getMigrationErrors(12L)).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));
    Path workingDir = service.createWorkingDirectory("IPA");
    Path root = workingDir.getParent();

    try {
      assertThrows(InvalidFileException.class, () -> service.retrieveAndUnzipFile(12L, workingDir));
    } finally {
      service.deleteWorkingDirectory(workingDir);
    }
    assertFalse(Files.exists(root));
  }

  @Test
  void cleansUpWhenExtractionFailsAfterWritingAFile() throws IOException {
    when(migrationService.getMigrationErrors(12L))
      .thenReturn(new ByteArrayResource(zip("ok.csv", "one", "_invalid.csv", "two")));
    Path workingDir = service.createWorkingDirectory("IPA");
    Path root = workingDir.getParent();

    try {
      assertThrows(InvalidFileException.class, () -> service.retrieveAndUnzipFile(12L, workingDir));
      try (var files = Files.walk(workingDir)) {
        assertTrue(files.anyMatch(path -> path.getFileName().toString().equals("ok.csv")));
      }
    } finally {
      service.deleteWorkingDirectory(workingDir);
    }
    assertFalse(Files.exists(root));
  }

  @Test
  void rejectsDuplicateResolvedEntriesBeforeExtraction() throws IOException {
    when(migrationService.getMigrationErrors(12L))
      .thenReturn(new ByteArrayResource(zip("folder/file.csv", "one", "folder/./file.csv", "two")));
    Path workingDir = service.createWorkingDirectory("IPA");
    try {
      assertThrows(InvalidFileException.class, () -> service.retrieveAndUnzipFile(12L, workingDir));
    } finally {
      service.deleteWorkingDirectory(workingDir);
    }
  }

  @Test
  void rejectsEntriesOutsideExtractionDirectory() throws IOException {
    when(migrationService.getMigrationErrors(12L))
      .thenReturn(new ByteArrayResource(zip("../outside.csv", "data")));
    Path workingDir = service.createWorkingDirectory("IPA");
    try {
      assertThrows(InvalidFileException.class, () -> service.retrieveAndUnzipFile(12L, workingDir));
      assertFalse(Files.exists(workingDir.resolve("outside.csv")));
    } finally {
      service.deleteWorkingDirectory(workingDir);
    }
  }

  @Test
  void propagatesDownloadFailureAndAllowsCleanup() throws IOException {
    Resource resource = mock(Resource.class);
    IOException failure = new IOException("download failed");
    when(migrationService.getMigrationErrors(12L)).thenReturn(resource);
    when(resource.getInputStream()).thenThrow(failure);
    Path workingDir = service.createWorkingDirectory("IPA");
    Path root = workingDir.getParent();
    try {
      assertSame(failure, assertThrows(IOException.class, () -> service.retrieveAndUnzipFile(12L, workingDir)));
    } finally {
      service.deleteWorkingDirectory(workingDir);
    }
    assertFalse(Files.exists(root));
  }

  @Test
  void rejectsMissingResourceAndUnsafeArguments() throws IOException {
    Path workingDir = service.createWorkingDirectory("IPA");
    Path unrelated = Files.createDirectory(tempDir.resolve("unrelated"));
    try {
      when(migrationService.getMigrationErrors(12L)).thenReturn(null);
      assertThrows(InvalidFileException.class, () -> service.retrieveAndUnzipFile(12L, workingDir));
      assertThrows(IllegalArgumentException.class, () -> service.retrieveAndUnzipFile(0L, workingDir));
      assertThrows(IllegalArgumentException.class, () -> service.createWorkingDirectory("../outside"));
      assertThrows(IllegalArgumentException.class, () -> service.deleteWorkingDirectory(unrelated));
      assertTrue(Files.exists(unrelated));
    } finally {
      service.deleteWorkingDirectory(workingDir);
    }
  }

  private byte[] zip(String... namesAndContents) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (int i = 0; i < namesAndContents.length; i += 2) {
        zip.putNextEntry(new ZipEntry(namesAndContents[i]));
        zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }
}
