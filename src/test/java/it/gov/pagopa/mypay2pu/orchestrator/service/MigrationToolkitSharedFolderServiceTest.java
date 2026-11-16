package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.config.MigrationTransferProperties;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

  private MigrationTransferProperties properties(Path outputPath) {
    return new MigrationTransferProperties(
      new MigrationTransferProperties.Storage(outputPath.toString())
    );
  }
}
