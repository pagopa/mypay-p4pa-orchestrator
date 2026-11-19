package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    Map<String, Long> fileSizes = new LinkedHashMap<>();
    fileSizes.put("IPA_TEST_2-part-1.csv", 500L);
    fileSizes.put("IPA_TEST_3-part-2.csv", 0L);

    List<MigrationFileExecutionDetail> result = mapper.toMigrationFileExecutionDetails(FILE_EXECUTION_ID, fileSizes);

    assertEquals(List.of("IPA_TEST_2-part-1.csv", "IPA_TEST_3-part-2.csv"),
      result.stream().map(MigrationFileExecutionDetail::getFileName).toList());
    assertEquals(List.of("IPA_TEST_2", "IPA_TEST_3"),
      result.stream().map(MigrationFileExecutionDetail::getIpaCode).toList());
    assertEquals(List.of(500L, 0L), result.stream().map(MigrationFileExecutionDetail::getFileSize).toList());
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
    assertEquals(List.of(), mapper.toMigrationFileExecutionDetails(FILE_EXECUTION_ID, Map.of()));
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
    assertEquals(10, result.getNumTotalRows());
    assertEquals(8, result.getNumCorrectlyImportedRows());
    assertEquals("Two rejected rows", result.getErrorDescription());
    assertEquals("rejects.csv", result.getDiscardFileName());
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

}
