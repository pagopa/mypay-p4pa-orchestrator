package it.gov.pagopa.mypay2pu.orchestrator.service.file;

import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidFileException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

@Component
public class RejectFileSelector {

  // TODO: invoke for each source detail using only files retrieved for its upload.
  public Path select(List<Path> retrievedFiles, String discardFileName) {
    if (!StringUtils.hasText(discardFileName) || discardFileName.contains("/") || discardFileName.contains("\\")) {
      throw new InvalidFileException("Invalid discard file name");
    }
    if (retrievedFiles == null || retrievedFiles.stream().anyMatch(path -> path == null || path.getFileName() == null)) {
      throw new InvalidFileException("Missing or invalid retrieved reject files");
    }

    List<Path> matches = retrievedFiles.stream()
      .filter(path -> discardFileName.equals(path.getFileName().toString()))
      .toList();
    if (matches.size() != 1 || !Files.isRegularFile(matches.getFirst(), LinkOption.NOFOLLOW_LINKS)) {
      throw new InvalidFileException("Discard file is missing, ambiguous or not a regular file: " + discardFileName);
    }
    return matches.getFirst();
  }
}
