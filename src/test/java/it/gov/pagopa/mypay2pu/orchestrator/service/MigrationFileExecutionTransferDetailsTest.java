package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationFileExecutionDetailMapper;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferValidator;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationFileExecutionTransferDetailsTest {

  private static final UUID MIGRATION_ID = UUID.fromString("93cbdb5c-83ca-43d9-8946-68f36b89d8c1");
  private static final UUID FILE_EXECUTION_ID = UUID.fromString("2f980de8-3891-4f69-96b8-f95de4f1d679");
  private static final MigrationFileType FILE_TYPE = MigrationFileType.ORGANIZATIONS;
  private static final Long UPLOAD_ID = 42L;

  @Mock
  private MigrationFileExecutionRepository migrationFileExecutionRepositoryMock;
  @Mock
  private MigrationFileExecutionDetailRepository migrationFileExecutionDetailRepositoryMock;

  private MigrationFileExecutionTransferService service;

  @BeforeEach
  void setUp() {
    service = new MigrationFileExecutionTransferService(
      migrationFileExecutionRepositoryMock,
      migrationFileExecutionDetailRepositoryMock,
      new MigrationFileExecutionDetailMapper(),
      new MigrationFileExecutionTransferValidator()
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      migrationFileExecutionRepositoryMock,
      migrationFileExecutionDetailRepositoryMock
    );
  }

  @ParameterizedTest
  @MethodSource("puUploadStatusMappings")
  void givenPuUploadDetailWhenRecordUploadDetailsThenPersistsNormalizedStatus(
    IngestionFlowFileStatus puStatus,
    MigrationFileExecutionDetailStatus expectedStatus
  ) {
    MigrationFileExecution fileExecution = waitingPuExecution();
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(FILE_EXECUTION_ID))
      .thenReturn(List.of());
    UploadDetails uploadDetail = uploadDetail(puStatus, 12L, 10L);
    MigrationFileExecutionDetail expectedDetail = expectedDetail(expectedStatus, 12, 10);
    List<MigrationFileExecutionDetail> expectedDetails = List.of(expectedDetail);
    when(migrationFileExecutionDetailRepositoryMock.saveAll(expectedDetails)).thenReturn(expectedDetails);

    List<MigrationFileExecutionDetail> result = service.recordUploadDetails(fileExecution, List.of(uploadDetail));

    assertEquals(expectedDetails, result);
    verify(migrationFileExecutionRepositoryMock).findByIdForUpdate(FILE_EXECUTION_ID);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(FILE_EXECUTION_ID);
    verify(migrationFileExecutionDetailRepositoryMock).saveAll(expectedDetails);
  }

  @Test
  void givenUploadDetailWithOutOfRangeRowCountWhenRecordUploadDetailsThenRejectsWithoutSaving() {
    MigrationFileExecution fileExecution = waitingPuExecution();
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(FILE_EXECUTION_ID))
      .thenReturn(List.of());
    List<UploadDetails> uploadDetails = List.of(
      uploadDetail(IngestionFlowFileStatus.COMPLETED, Long.MAX_VALUE, 1L)
    );

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> service.recordUploadDetails(fileExecution, uploadDetails)
    );

    assertEquals("TRANSFER_INVALID_UPLOAD_DETAILS", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findByIdForUpdate(FILE_EXECUTION_ID);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(FILE_EXECUTION_ID);
  }

  @Test
  void givenUploadDetailWithoutRowCountsWhenRecordUploadDetailsThenPersistsNullCounts() {
    MigrationFileExecution fileExecution = waitingPuExecution();
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(FILE_EXECUTION_ID))
      .thenReturn(List.of());
    UploadDetails uploadDetail = uploadDetail(IngestionFlowFileStatus.PROCESSING, null, null);
    List<MigrationFileExecutionDetail> expectedDetails = List.of(expectedDetail(
      MigrationFileExecutionDetailStatus.WAITING_PU,
      null,
      null
    ));
    when(migrationFileExecutionDetailRepositoryMock.saveAll(expectedDetails)).thenReturn(expectedDetails);

    List<MigrationFileExecutionDetail> result = service.recordUploadDetails(fileExecution, List.of(uploadDetail));

    assertEquals(expectedDetails, result);
    assertNull(result.getFirst().getNumTotalRows());
    assertNull(result.getFirst().getNumCorrectlyImportedRows());
    verify(migrationFileExecutionRepositoryMock).findByIdForUpdate(FILE_EXECUTION_ID);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(FILE_EXECUTION_ID);
    verify(migrationFileExecutionDetailRepositoryMock).saveAll(expectedDetails);
  }

  @Test
  void givenUploadDetailsForDifferentUploadWhenRecordUploadDetailsThenRejectsWithoutSaving() {
    MigrationFileExecution fileExecution = waitingPuExecution();
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(FILE_EXECUTION_ID))
      .thenReturn(List.of());
    UploadDetails uploadDetail = uploadDetail(IngestionFlowFileStatus.COMPLETED, 12L, 10L);
    uploadDetail.setUploadId(43L);
    List<UploadDetails> uploadDetails = List.of(uploadDetail);

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> service.recordUploadDetails(fileExecution, uploadDetails)
    );

    assertEquals("TRANSFER_INVALID_UPLOAD_DETAILS", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findByIdForUpdate(FILE_EXECUTION_ID);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(FILE_EXECUTION_ID);
  }

  @Test
  void givenNullUploadIdWhenRecordUploadDetailsThenRejectsBeforeReadingDetails() {
    MigrationFileExecution fileExecution = MigrationFileExecution.builder().id(FILE_EXECUTION_ID).build();

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> service.recordUploadDetails(fileExecution, List.of())
    );

    assertEquals("INVALID_UPLOAD_ID", exception.getCode());
    verifyNoInteractions(migrationFileExecutionRepositoryMock, migrationFileExecutionDetailRepositoryMock);
  }

  @Test
  void givenExistingUploadDetailsWhenRecordUploadDetailsThenReturnsPersistedDetails() {
    MigrationFileExecution fileExecution = waitingPuExecution();
    MigrationFileExecutionDetail persistedDetail = expectedDetail(
      MigrationFileExecutionDetailStatus.COMPLETED,
      12,
      10
    );
    List<MigrationFileExecutionDetail> persistedDetails = List.of(persistedDetail);
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(FILE_EXECUTION_ID))
      .thenReturn(persistedDetails);

    List<MigrationFileExecutionDetail> result = service.recordUploadDetails(
      fileExecution,
      List.of(uploadDetail(IngestionFlowFileStatus.PROCESSING, 12L, 10L))
    );

    assertEquals(persistedDetails, result);
    verify(migrationFileExecutionRepositoryMock).findByIdForUpdate(FILE_EXECUTION_ID);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(FILE_EXECUTION_ID);
  }

  @Test
  void givenAcceptedUploadWithoutPersistedDetailsWhenHasUploadDetailsThenReturnsFalse() {
    MigrationFileExecution fileExecution = MigrationFileExecution.builder()
      .id(FILE_EXECUTION_ID)
      .migrationId(MIGRATION_ID)
      .fileType(FILE_TYPE)
      .status(MigrationFileExecutionStatus.WAITING_PU)
      .uploadId(UPLOAD_ID)
      .build();
    when(migrationFileExecutionDetailRepositoryMock.existsByMigrationFileExecutionId(FILE_EXECUTION_ID))
      .thenReturn(false);

    boolean result = service.hasUploadDetails(fileExecution);

    assertFalse(result);
    verifyNoInteractions(migrationFileExecutionRepositoryMock);
    verify(migrationFileExecutionDetailRepositoryMock).existsByMigrationFileExecutionId(FILE_EXECUTION_ID);
  }

  private static Stream<Arguments> puUploadStatusMappings() {
    return Stream.of(
      Arguments.of(IngestionFlowFileStatus.UPLOADED, MigrationFileExecutionDetailStatus.WAITING_PU),
      Arguments.of(IngestionFlowFileStatus.PROCESSING, MigrationFileExecutionDetailStatus.WAITING_PU),
      Arguments.of(IngestionFlowFileStatus.WAITING_FILE, MigrationFileExecutionDetailStatus.WAITING_PU),
      Arguments.of(IngestionFlowFileStatus.COMPLETED, MigrationFileExecutionDetailStatus.COMPLETED),
      Arguments.of(IngestionFlowFileStatus.WARNING, MigrationFileExecutionDetailStatus.PARTIAL),
      Arguments.of(IngestionFlowFileStatus.ERROR, MigrationFileExecutionDetailStatus.FAILED)
    );
  }

  private MigrationFileExecution waitingPuExecution() {
    MigrationFileExecution file = MigrationFileExecution.builder()
      .id(FILE_EXECUTION_ID)
      .migrationId(MIGRATION_ID)
      .fileType(FILE_TYPE)
      .status(MigrationFileExecutionStatus.WAITING_PU)
      .uploadId(UPLOAD_ID)
      .build();
    when(migrationFileExecutionRepositoryMock.findByIdForUpdate(FILE_EXECUTION_ID))
      .thenReturn(Optional.of(file));
    return file;
  }

  private UploadDetails uploadDetail(IngestionFlowFileStatus status, Long totalRows, Long importedRows) {
    return UploadDetails.builder()
      .uploadDetailId(123L)
      .uploadId(UPLOAD_ID)
      .organizationId(1L)
      .ingestionFlowFileType(IngestionFlowFileTypeEnum.ORGANIZATIONS)
      .filePathName("uploads/organizations.csv")
      .fileName("organizations.csv")
      .fileSize(500L)
      .status(status)
      .errorDescription("error")
      .discardFileName("organizations.errors.csv")
      .numTotalRows(totalRows)
      .numCorrectlyImportedRows(importedRows)
      .build();
  }

  private MigrationFileExecutionDetail expectedDetail(
    MigrationFileExecutionDetailStatus status,
    Integer totalRows,
    Integer importedRows
  ) {
    return MigrationFileExecutionDetail.builder()
      .migrationFileExecutionId(FILE_EXECUTION_ID)
      .fileName("organizations.csv")
      .uploadDetailId(123L)
      .fileSize(500L)
      .status(status)
      .errorDescription("error")
      .discardFileName("organizations.errors.csv")
      .numTotalRows(totalRows)
      .numCorrectlyImportedRows(importedRows)
      .build();
  }
}
