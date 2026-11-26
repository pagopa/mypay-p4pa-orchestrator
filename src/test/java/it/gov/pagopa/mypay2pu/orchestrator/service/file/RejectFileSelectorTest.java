package it.gov.pagopa.mypay2pu.orchestrator.service.file;

import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RejectFileSelectorTest {

  @TempDir
  private Path tempDir;
  private final RejectFileSelector selector = new RejectFileSelector();

  @Test
  void selectsExactFileNameForCsvAndNestedArchive() throws IOException {
    Path csv = Files.createFile(tempDir.resolve("ERROR-one.csv"));
    Path zip = Files.createFile(tempDir.resolve("ERROR-two.zip"));
    List<Path> files = List.of(csv, zip);

    assertEquals(csv, selector.select(files, "ERROR-one.csv"));
    assertEquals(zip, selector.select(files, "ERROR-two.zip"));
  }

  @Test
  void rejectsMissingOrAmbiguousFileWithoutGuessing() throws IOException {
    Path first = Files.createFile(Files.createDirectory(tempDir.resolve("a")).resolve("ERROR.zip"));
    Path second = Files.createFile(Files.createDirectory(tempDir.resolve("b")).resolve("ERROR.zip"));
    List<Path> singlePathList = List.of(first);
    List<Path> pathList = List.of(first, second);

    assertThrows(InvalidFileException.class, () -> selector.select(singlePathList, "error.zip"));
    assertThrows(InvalidFileException.class, () -> selector.select(pathList, "ERROR.zip"));
    assertThrows(InvalidFileException.class, () -> selector.select(List.of(), "ERROR.zip"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "../ERROR.zip", "dir/ERROR.zip", "dir\\ERROR.zip"})
  void rejectsInvalidDiscardFileNames(String name) {
    assertThrows(InvalidFileException.class, () -> selector.select(List.of(), name));
  }

  @Test
  void rejectsMissingListAndNonRegularMatches() throws IOException {
    Path directory = Files.createDirectory(tempDir.resolve("ERROR.zip"));
    List<Path> missing = List.of(tempDir.resolve("missing.csv"));
    List<Path> directoryList = List.of(directory);
    List<Path> directories = Arrays.asList(directory, null);
    List<Path> root = List.of(tempDir.getRoot());

    assertThrows(InvalidFileException.class, () -> selector.select(null, "ERROR.zip"));
    assertThrows(InvalidFileException.class, () -> selector.select(missing, "missing.csv"));
    assertThrows(InvalidFileException.class, () -> selector.select(directoryList, "ERROR.zip"));
    assertThrows(InvalidFileException.class, () -> selector.select(directories, "ERROR.zip"));
    assertThrows(InvalidFileException.class, () -> selector.select(root, "ERROR.zip"));
  }
}
