package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidFileException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class FileValidator {

  public void requireWorkingDirectory(Path workingDir, Set<Path> workingDirectories) {
    if (workingDir == null || !workingDirectories.contains(workingDir)) {
      throw new IllegalArgumentException("Working directory was not created by this service");
    }
  }

  public void validateArchive(Path archive, Path extractedDir) {
    Set<Path> entries = new HashSet<>();
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      Enumeration<? extends ZipEntry> enumeration = zip.entries();
      while (enumeration.hasMoreElements()) {
        ZipEntry entry = enumeration.nextElement();
        Path target = extractedDir.resolve(entry.getName()).normalize();
        if (!target.startsWith(extractedDir) || !entries.add(target)) {
          throw new InvalidFileException("Invalid or duplicate entry in error archive: " + entry.getName());
        }
      }
    } catch (IOException e) {
      throw new InvalidFileException("Invalid error archive: " + archive, e);
    }
  }
}
