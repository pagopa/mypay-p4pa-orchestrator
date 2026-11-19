package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static it.gov.pagopa.mypay2pu.orchestrator.utils.TestUtils.checkNotNullFields;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationFileExecutionDetailMapperTest {

  private static final UUID FILE_EXECUTION_ID = UUID.fromString("2f980de8-3891-4f69-96b8-f95de4f1d679");

  private final MigrationFileExecutionDetailMapper mapper = new MigrationFileExecutionDetailMapper();

  @Test
  void givenExtractedFileWhenToMigrationFileExecutionDetailThenMapsExportedMetadata() {
    MigrationFileExecutionDetail result = mapper.toMigrationFileExecutionDetail(
      FILE_EXECUTION_ID, "IPA_TEST_2-dovuti-C140-Secondario-1_4.csv", 500L
    );

    checkNotNullFields(result, "id", "creationDate", "updateDate", "updateTraceId",
      "uploadDetailId", "errorDescription", "discardFileName", "numTotalRows", "numCorrectlyImportedRows");
    assertEquals(FILE_EXECUTION_ID, result.getMigrationFileExecutionId());
    assertEquals("IPA_TEST_2-dovuti-C140-Secondario-1_4.csv", result.getFileName());
    assertEquals(500L, result.getFileSize());
    assertEquals(MigrationFileExecutionDetailStatus.EXPORTED, result.getStatus());
    assertEquals("IPA_TEST_2", result.getIpaCode());
    assertNull(result.getUploadDetailId());
    assertNull(result.getNumTotalRows());
    assertNull(result.getNumCorrectlyImportedRows());
  }

  @Test
  void givenInvalidExtractedFileWhenToMigrationFileExecutionDetailThenRejectsMetadata() {
    assertThrows(InvalidValueException.class,
      () -> mapper.toMigrationFileExecutionDetail(FILE_EXECUTION_ID, " ", 10L));
    assertThrows(InvalidValueException.class,
      () -> mapper.toMigrationFileExecutionDetail(FILE_EXECUTION_ID, "IPA_TEST_2-organizations.csv", -1L));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "organizations.csv", "-organizations.csv", " -organizations.csv", "IPA_TEST_2-"})
  void givenInvalidFileNameWhenToMigrationFileExecutionDetailThenRejectsMissingIpa(String fileName) {
    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> mapper.toMigrationFileExecutionDetail(FILE_EXECUTION_ID, fileName, 500L));

    assertEquals("EXTRACTOR_INVALID_FILE_DETAILS", failure.getCode());
  }

  @Test
  void givenOversizedIpaWhenToMigrationFileExecutionDetailThenRejectsMetadata() {
    String fileName = "I".repeat(51) + "-organizations.csv";

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> mapper.toMigrationFileExecutionDetail(FILE_EXECUTION_ID, fileName, 500L));

    assertEquals("EXTRACTOR_INVALID_FILE_DETAILS", failure.getCode());
    assertEquals("Cannot determine IPA code from extracted file " + fileName, failure.getMessage());
  }

  @Test
  void givenNestedFileWithMaximumLengthIpaWhenToMigrationFileExecutionDetailThenMapsIpa() {
    String ipaCode = "I".repeat(50);
    String fileName = "directory-with-hyphens/" + ipaCode + "-organizations.csv";

    MigrationFileExecutionDetail result = mapper.toMigrationFileExecutionDetail(FILE_EXECUTION_ID, fileName, 500L);

    checkNotNullFields(result, "id", "creationDate", "updateDate", "updateTraceId",
      "uploadDetailId", "errorDescription", "discardFileName", "numTotalRows", "numCorrectlyImportedRows");
    assertEquals(ipaCode, result.getIpaCode());
    assertEquals(fileName, result.getFileName());
  }

  @Test
  void givenMultipleExtractedFilesWhenToMigrationFileExecutionDetailsThenMapsEachFileInOrder() {
    List<ArchiveFile> archives = List.of(
      new ArchiveFile("first.zip", List.of("IPA_TEST_2-part-1.csv")),
      new ArchiveFile("second.zip", List.of("IPA_TEST_3-part-2.csv"))
    );

    List<MigrationFileExecutionDetail> result = mapper.toMigrationFileExecutionDetails(FILE_EXECUTION_ID, archives);

    assertEquals(List.of("IPA_TEST_2-part-1.csv", "IPA_TEST_3-part-2.csv"),
      result.stream().map(MigrationFileExecutionDetail::getFileName).toList());
    assertEquals(List.of("IPA_TEST_2", "IPA_TEST_3"),
      result.stream().map(MigrationFileExecutionDetail::getIpaCode).toList());
    assertEquals(List.of(0L, 0L), result.stream().map(MigrationFileExecutionDetail::getFileSize).toList());
    result.forEach(detail -> {
      checkNotNullFields(detail, "id", "creationDate", "updateDate", "updateTraceId",
        "uploadDetailId", "errorDescription", "discardFileName", "numTotalRows", "numCorrectlyImportedRows");
      assertEquals(FILE_EXECUTION_ID, detail.getMigrationFileExecutionId());
      assertEquals(MigrationFileExecutionDetailStatus.EXPORTED, detail.getStatus());
      assertNull(detail.getUploadDetailId());
      assertNull(detail.getErrorDescription());
      assertNull(detail.getDiscardFileName());
      assertNull(detail.getNumTotalRows());
      assertNull(detail.getNumCorrectlyImportedRows());
    });
  }

  @Test
  void givenNoExtractedFilesWhenToMigrationFileExecutionDetailsThenReturnsNoDetails() {
    assertEquals(List.of(), mapper.toMigrationFileExecutionDetails(FILE_EXECUTION_ID, List.of()));
  }

  @Test
  void givenEmptyDeclaredFileListWhenToMigrationFileExecutionDetailsThenReturnsNoDetails() {
    assertEquals(List.of(), mapper.toMigrationFileExecutionDetails(
      FILE_EXECUTION_ID, List.of(new ArchiveFile("data.zip", List.of()))
    ));
  }

  @Test
  void givenDataAndErrorArchivesWhenToMigrationFileExecutionDetailsThenExcludesErrors() {
    List<ArchiveFile> archives = List.of(
      new ArchiveFile("data.zip", List.of("IPA_TEST_2-organizations.csv", "nested/IPA_TEST_3-organizations.csv")),
      new ArchiveFile(" data.ERRORS.ZIP ", null)
    );

    List<MigrationFileExecutionDetail> result = mapper.toMigrationFileExecutionDetails(FILE_EXECUTION_ID, archives);

    assertEquals(List.of("IPA_TEST_2-organizations.csv", "nested/IPA_TEST_3-organizations.csv"),
      result.stream().map(MigrationFileExecutionDetail::getFileName).toList());
    result.forEach(detail -> {
      checkNotNullFields(detail, "id", "creationDate", "updateDate", "updateTraceId",
        "uploadDetailId", "errorDescription", "discardFileName", "numTotalRows", "numCorrectlyImportedRows");
      assertEquals(0L, detail.getFileSize());
      assertEquals(MigrationFileExecutionDetailStatus.EXPORTED, detail.getStatus());
    });
  }

  @ParameterizedTest
  @MethodSource("invalidDeclaredFiles")
  void givenInvalidDeclaredFilesWhenToMigrationFileExecutionDetailsThenRejectsMetadata(List<String> files) {
    List<ArchiveFile> archives = List.of(new ArchiveFile("data.zip", files));

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> mapper.toMigrationFileExecutionDetails(FILE_EXECUTION_ID, archives));

    assertEquals("EXTRACTOR_INVALID_FILE_DETAILS", failure.getCode());
  }

  @Test
  void givenDuplicateFilesAcrossArchivesWhenToMigrationFileExecutionDetailsThenRejectsMetadata() {
    List<ArchiveFile> archives = List.of(
      new ArchiveFile("first.zip", List.of("IPA_TEST_2-organizations.csv")),
      new ArchiveFile("second.zip", List.of("IPA_TEST_2-organizations.csv"))
    );

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> mapper.toMigrationFileExecutionDetails(FILE_EXECUTION_ID, archives));

    assertEquals("EXTRACTOR_INVALID_FILE_DETAILS", failure.getCode());
  }

  private static Stream<List<String>> invalidDeclaredFiles() {
    return Stream.of(null, List.of(" "), Collections.singletonList(null),
      List.of("organizations.csv"), List.of("IPA_TEST_2-part.csv", "IPA_TEST_2-part.csv"));
  }
}
