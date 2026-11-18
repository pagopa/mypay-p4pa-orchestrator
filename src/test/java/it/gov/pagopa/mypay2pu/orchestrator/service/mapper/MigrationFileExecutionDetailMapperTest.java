package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static it.gov.pagopa.mypay2pu.orchestrator.utils.TestUtils.checkNotNullFields;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationFileExecutionDetailMapperTest {

  private static final UUID FILE_EXECUTION_ID = UUID.fromString("2f980de8-3891-4f69-96b8-f95de4f1d679");
  private static final Long UPLOAD_ID = 42L;

  private final MigrationFileExecutionDetailMapper mapper = new MigrationFileExecutionDetailMapper();

  @ParameterizedTest
  @MethodSource("invalidMetadata")
  void givenInvalidMetadataWhenToMigrationFileExecutionDetailThenRejectsUploadDetail(UploadDetails uploadDetail) {
    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> mapper.toMigrationFileExecutionDetail(FILE_EXECUTION_ID, UPLOAD_ID, uploadDetail));

    assertEquals("TRANSFER_INVALID_UPLOAD_DETAILS", failure.getCode());
    assertEquals("PU returned invalid upload details for upload 42", failure.getMessage());
  }

  static Stream<UploadDetails> invalidMetadata() {
    return Stream.of(
      new UploadDetails().uploadId(UPLOAD_ID).fileName(" ").fileSize(500L)
        .status(IngestionFlowFileStatus.COMPLETED),
      new UploadDetails().uploadId(UPLOAD_ID).fileName("organizations.csv").fileSize(-1L)
        .status(IngestionFlowFileStatus.COMPLETED)
    );
  }

  @Test
  void givenCompletedUploadDetailWhenToMigrationFileExecutionDetailThenMapsAllFields() {
    UploadDetails uploadDetail = UploadDetails.builder()
      .uploadDetailId(123L)
      .uploadId(UPLOAD_ID)
      .organizationId(1L)
      .ingestionFlowFileType(IngestionFlowFileTypeEnum.ORGANIZATIONS)
      .filePathName("uploads/organizations.csv")
      .fileName("organizations.csv")
      .fileSize(500L)
      .status(IngestionFlowFileStatus.COMPLETED)
      .errorDescription("error")
      .discardFileName("organizations.errors.csv")
      .numTotalRows(12L)
      .numCorrectlyImportedRows(10L)
      .build();

    MigrationFileExecutionDetail result = mapper.toMigrationFileExecutionDetail(
      FILE_EXECUTION_ID,
      UPLOAD_ID,
      uploadDetail
    );

    checkNotNullFields(result, "id", "creationDate", "updateDate", "updateTraceId");
    assertEquals(FILE_EXECUTION_ID, result.getMigrationFileExecutionId());
    assertEquals("organizations.csv", result.getFileName());
    assertEquals(123L, result.getUploadDetailId());
    assertEquals(500L, result.getFileSize());
    assertEquals(MigrationFileExecutionDetailStatus.COMPLETED, result.getStatus());
    assertEquals("error", result.getErrorDescription());
    assertEquals("organizations.errors.csv", result.getDiscardFileName());
    assertEquals(12, result.getNumTotalRows());
    assertEquals(10, result.getNumCorrectlyImportedRows());
  }
}
