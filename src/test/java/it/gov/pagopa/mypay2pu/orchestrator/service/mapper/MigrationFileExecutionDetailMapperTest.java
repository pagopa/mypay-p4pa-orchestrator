package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.Arguments;
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

  @ParameterizedTest
  @MethodSource("puStatuses")
  void givenPuDetailWhenUpdateMigrationFileExecutionDetailThenEnrichesExportWithoutReplacingMetadata(
    IngestionFlowFileStatus puStatus, MigrationFileExecutionDetailStatus expected
  ) {
    LocalDateTime createdAt = LocalDateTime.of(2026, Month.NOVEMBER, 17, 12, 0);
    UUID detailId = UUID.randomUUID();
    MigrationFileExecutionDetail result = mapper.toMigrationFileExecutionDetail(
      FILE_EXECUTION_ID, "IPA_TEST_2-organizations.csv", 500L
    );
    result.setId(detailId);
    result.setCreationDate(createdAt);
    result.setUpdateDate(createdAt);
    result.setUpdateTraceId("trace");
    result.setStatus(MigrationFileExecutionDetailStatus.WAITING_PU);
    UploadDetails detail = new UploadDetails().uploadId(42L).uploadDetailId(123L)
      .fileName(result.getFileName()).fileSize(500L).status(puStatus)
      .numTotalRows(10L).numCorrectlyImportedRows(8L)
      .errorDescription("Two rejected rows").discardFileName("rejects.csv");

    mapper.updateMigrationFileExecutionDetail(result, detail);

    checkNotNullFields(result);
    assertEquals(detailId, result.getId());
    assertEquals(FILE_EXECUTION_ID, result.getMigrationFileExecutionId());
    assertEquals("IPA_TEST_2-organizations.csv", result.getFileName());
    assertEquals("IPA_TEST_2", result.getIpaCode());
    assertEquals(500L, result.getFileSize());
    assertEquals(createdAt, result.getCreationDate());
    assertEquals(createdAt, result.getUpdateDate());
    assertEquals("trace", result.getUpdateTraceId());
    assertEquals(123L, result.getUploadDetailId());
    assertEquals(expected, result.getStatus());
    assertEquals(10L, result.getNumTotalRows());
    assertEquals(8L, result.getNumCorrectlyImportedRows());
    assertEquals("Two rejected rows", result.getErrorDescription());
    assertEquals("rejects.csv", result.getDiscardFileName());
  }

  @Test
  void givenLongCountsWhenUpdateMigrationFileExecutionDetailThenPreservesFullPrecision() {
    MigrationFileExecutionDetail result = mapper.toMigrationFileExecutionDetail(
      FILE_EXECUTION_ID, "IPA_TEST_2-organizations.csv", 500L);
    UploadDetails detail = new UploadDetails().uploadId(42L).uploadDetailId(123L)
      .fileSize(500L).status(IngestionFlowFileStatus.WARNING)
      .numTotalRows(Long.MAX_VALUE).numCorrectlyImportedRows(Long.MAX_VALUE - 1);

    mapper.updateMigrationFileExecutionDetail(result, detail);

    checkNotNullFields(result, "id", "creationDate", "updateDate", "updateTraceId",
      "errorDescription", "discardFileName");
    assertEquals(Long.MAX_VALUE, result.getNumTotalRows());
    assertEquals(Long.MAX_VALUE - 1, result.getNumCorrectlyImportedRows());
  }

  static Stream<Arguments> puStatuses() {
    return Stream.of(
      Arguments.of(IngestionFlowFileStatus.UPLOADED, MigrationFileExecutionDetailStatus.WAITING_PU),
      Arguments.of(IngestionFlowFileStatus.PROCESSING, MigrationFileExecutionDetailStatus.WAITING_PU),
      Arguments.of(IngestionFlowFileStatus.WAITING_FILE, MigrationFileExecutionDetailStatus.WAITING_PU),
      Arguments.of(IngestionFlowFileStatus.COMPLETED, MigrationFileExecutionDetailStatus.COMPLETED),
      Arguments.of(IngestionFlowFileStatus.WARNING, MigrationFileExecutionDetailStatus.PARTIAL),
      Arguments.of(IngestionFlowFileStatus.ERROR, MigrationFileExecutionDetailStatus.FAILED)
    );
  }

  @Test
  void givenOmittedPuFieldsWhenUpdateMigrationFileExecutionDetailThenReplacesPreviousValuesWithCurrentSnapshot() {
    MigrationFileExecutionDetail result = mapper.toMigrationFileExecutionDetail(
      FILE_EXECUTION_ID, "IPA_TEST_2-organizations.csv", 500L);
    result.setStatus(MigrationFileExecutionDetailStatus.WAITING_PU);
    UploadDetails first = new UploadDetails().uploadId(42L).uploadDetailId(123L)
      .fileSize(500L).status(IngestionFlowFileStatus.COMPLETED).numTotalRows(10L).numCorrectlyImportedRows(3L)
      .errorDescription("Rejected rows").discardFileName("rejects.csv");
    mapper.updateMigrationFileExecutionDetail(result, first);

    mapper.updateMigrationFileExecutionDetail(result, new UploadDetails().uploadId(42L).uploadDetailId(123L)
      .fileSize(501L).status(IngestionFlowFileStatus.PROCESSING).numCorrectlyImportedRows(4L));

    checkNotNullFields(result, "id", "creationDate", "updateDate", "updateTraceId",
      "numTotalRows", "errorDescription", "discardFileName");
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, result.getStatus());
    assertEquals(123L, result.getUploadDetailId());
    assertEquals(501L, result.getFileSize());
    assertNull(result.getNumTotalRows());
    assertEquals(4L, result.getNumCorrectlyImportedRows());
    assertNull(result.getErrorDescription());
    assertNull(result.getDiscardFileName());
  }

  @Test
  void givenProvisionalSizeWhenUpdateMigrationFileExecutionDetailThenAcquiresPuSize() {
    MigrationFileExecutionDetail result = mapper.toMigrationFileExecutionDetail(
      FILE_EXECUTION_ID, "IPA_TEST_2-organizations.csv", 0L);
    UploadDetails detail = new UploadDetails().uploadId(42L).uploadDetailId(123L)
      .fileSize(500L).status(IngestionFlowFileStatus.PROCESSING);

    mapper.updateMigrationFileExecutionDetail(result, detail);

    checkNotNullFields(result, "id", "creationDate", "updateDate", "updateTraceId",
      "errorDescription", "discardFileName", "numTotalRows", "numCorrectlyImportedRows");
    assertEquals(500L, result.getFileSize());
    assertEquals(123L, result.getUploadDetailId());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, result.getStatus());
  }

  private static Stream<List<String>> invalidDeclaredFiles() {
    return Stream.of(null, List.of(" "), Collections.singletonList(null),
      List.of("organizations.csv"), List.of("IPA_TEST_2-part.csv", "IPA_TEST_2-part.csv"));
  }
}
