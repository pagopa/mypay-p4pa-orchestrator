package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
      FILE_EXECUTION_ID, "organizations.csv", 500L
    );

    checkNotNullFields(result, "id", "creationDate", "updateDate", "updateTraceId",
      "uploadDetailId", "errorDescription", "discardFileName", "numTotalRows", "numCorrectlyImportedRows");
    assertEquals(FILE_EXECUTION_ID, result.getMigrationFileExecutionId());
    assertEquals("organizations.csv", result.getFileName());
    assertEquals(500L, result.getFileSize());
    assertEquals(MigrationFileExecutionDetailStatus.EXPORTED, result.getStatus());
    assertNull(result.getUploadDetailId());
    assertNull(result.getNumTotalRows());
    assertNull(result.getNumCorrectlyImportedRows());
  }

  @Test
  void givenInvalidExtractedFileWhenToMigrationFileExecutionDetailThenRejectsMetadata() {
    assertThrows(InvalidValueException.class,
      () -> mapper.toMigrationFileExecutionDetail(FILE_EXECUTION_ID, " ", 10L));
    assertThrows(InvalidValueException.class,
      () -> mapper.toMigrationFileExecutionDetail(FILE_EXECUTION_ID, "organizations.csv", -1L));
  }

  @Test
  void givenMultipleExtractedFilesWhenToMigrationFileExecutionDetailsThenMapsEachFileInOrder() {
    Map<String, Long> fileSizes = new LinkedHashMap<>();
    fileSizes.put("part-1.csv", 500L);
    fileSizes.put("part-2.csv", 0L);

    List<MigrationFileExecutionDetail> result = mapper.toMigrationFileExecutionDetails(FILE_EXECUTION_ID, fileSizes);

    assertEquals(List.of("part-1.csv", "part-2.csv"),
      result.stream().map(MigrationFileExecutionDetail::getFileName).toList());
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
}
