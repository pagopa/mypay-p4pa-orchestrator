package it.gov.pagopa.mypay2pu.orchestrator.service.file;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidFileException;
import it.gov.pagopa.mypay2pu.orchestrator.validation.FileValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RejectArchiveService {

  private final PuMigrationService migrationService;
  private final ZipFileService zipFileService;
  private final FileValidator validator;
  private final Path tempBaseDir;
  private final Set<Path> workingDirectories = ConcurrentHashMap.newKeySet();

  public RejectArchiveService(PuMigrationService migrationService,
                              ZipFileService zipFileService,
                              FileValidator validator,
                              @Value("${orchestrator.storage.temp-base-dir}") String tempBaseDir) {
    this.migrationService = migrationService;
    this.zipFileService = zipFileService;
    this.validator = validator;
    this.tempBaseDir = Path.of(tempBaseDir).toAbsolutePath().normalize();
  }

  public Path createWorkingDirectory(String ipaCode) throws IOException {
    if (ipaCode == null || !ipaCode.matches("[A-Za-z0-9._-]+")
      || ipaCode.equals(".") || ipaCode.equals("..") || ipaCode.endsWith(".")) {
      throw new IllegalArgumentException("Invalid IPA code for working directory");
    }

    Path root = Files.createTempDirectory(tempBaseDir, "reject-");
    Path workingDir = root.resolve(ipaCode);
    try {
      Files.createDirectory(workingDir);
      workingDirectories.add(workingDir);
      return workingDir;
    } catch (IOException | RuntimeException e) {
      try {
        Files.delete(root);
      } catch (IOException cleanupError) {
        e.addSuppressed(cleanupError);
      }
      throw e;
    }
  }

  /**
   * Downloads and extracts an upload into a separate directory within the working directory.
   * The caller must delete the working directory in a finally block, including on failure.
   */
  // TODO: connect the reject detail lookup and CSV handler; resolve nested archives and match discardFileName.
  public List<Path> retrieveAndUnzipFile(Long uploadId, Path workingDir) throws IOException {
    validator.requireWorkingDirectory(workingDir, workingDirectories);
    if (uploadId == null || uploadId <= 0) {
      throw new IllegalArgumentException("uploadId must be positive");
    }
    Resource resource = migrationService.getMigrationErrors(uploadId);
    if (resource == null) {
      throw new InvalidFileException("No error archive returned for upload " + uploadId);
    }

    Path archiveDir = Files.createTempDirectory(workingDir, "upload-" + uploadId + "-");
    Path archive = archiveDir.resolve("errors.zip");
    Path extractedDir = Files.createDirectory(archiveDir.resolve("extracted"));
    try (InputStream input = resource.getInputStream()) {
      Files.copy(input, archive);
    }
    validator.validateArchive(archive, extractedDir);
    List<Path> extracted = zipFileService.unzip(archive, extractedDir);
    if (extracted.isEmpty()) {
      throw new InvalidFileException("Error archive contains no files for upload " + uploadId);
    }
    return extracted;
  }

  public void deleteWorkingDirectory(Path workingDir) throws IOException {
    validator.requireWorkingDirectory(workingDir, workingDirectories);
    Path root = workingDir.getParent();
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
        if (error != null) {
          throw error;
        }
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
    workingDirectories.remove(workingDir);
  }

}
