package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.ExportFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.TransferFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationStatusMapper;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static it.gov.pagopa.mypay2pu.orchestrator.utils.TestUtils.checkNotNullFields;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationStatusServiceImplTest {

  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, Month.JANUARY, 10, 10, 15);
  private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, Month.JANUARY, 10, 11, 30);
  private static final LocalDateTime EXTRACTED_AT = LocalDateTime.of(2026, Month.JANUARY, 10, 10, 45);
  private static final LocalDateTime UPLOADED_AT = LocalDateTime.of(2026, Month.JANUARY, 10, 11, 0);

  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;
  @Mock
  private MigrationFileExecutionRepository migrationFileExecutionRepositoryMock;
  @Mock
  private MigrationFileExecutionDetailRepository migrationFileExecutionDetailRepositoryMock;

  private MigrationStatusServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new MigrationStatusServiceImpl(
      migrationExecutionRepositoryMock,
      migrationFileExecutionRepositoryMock,
      migrationFileExecutionDetailRepositoryMock,
      new MigrationStatusMapper()
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      migrationExecutionRepositoryMock,
      migrationFileExecutionRepositoryMock,
      migrationFileExecutionDetailRepositoryMock
    );
  }

  @Test
  void returnsMigrationAndPersistedFileStatuses() {
    UUID migrationId = UUID.randomUUID();
    MigrationExecution execution = migrationExecution(migrationId);
    MigrationFileExecution fileExecution = fileExecution(migrationId, MigrationFileExecutionStatus.COMPLETED);
    fileExecution.setUploadStatus(UploadsStatusEnum.COMPLETED);
    MigrationFileExecutionDetail firstDetail = fileExecutionDetail(fileExecution.getId(), 10, 8, 700L);
    MigrationFileExecutionDetail secondDetail = fileExecutionDetail(fileExecution.getId(), 5, 4, 300L);
    firstDetail.setUpdateDate(EXTRACTED_AT);
    secondDetail.setUpdateDate(UPLOADED_AT);
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdOrderByCreationDateAscIdAsc(migrationId))
      .thenReturn(List.of(fileExecution));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionIdIn(List.of(fileExecution.getId())))
      .thenReturn(List.of(firstDetail, secondDetail));

    MigrationStatusDto response = service.getMigrationStatus(migrationId);

    assertEquals(migrationId, response.getMigrationId());
    assertEquals(MigrationAction.COMPLETE, response.getAction());
    assertEquals(MigrationExecutionStatus.RUNNING, response.getStatus());
    assertEquals(OrchestrationMode.FULL, response.getOrchestrationMode());
    assertEquals(List.of("80000000000"), response.getIpaCodes());
    assertEquals("MIGRATION_ERROR", response.getErrorCode());
    assertEquals("migration error", response.getErrorMsg());
    assertEquals(CREATED_AT.atZone(Constants.ZONEID).toOffsetDateTime(), response.getCreatedAt());
    assertEquals(UPDATED_AT.atZone(Constants.ZONEID).toOffsetDateTime(), response.getUpdatedAt());
    assertEquals(1, response.getExportPhase().getFiles().size());
    ExportFileStatusDto exportFile = response.getExportPhase().getFiles().getFirst();
    assertEquals(MigrationFileType.ORGANIZATIONS, exportFile.getFileType());
    assertEquals("extractor-1", exportFile.getExtractionId());
    assertEquals(15L, exportFile.getRecordCount());
    assertEquals(MigrationExecutionStatus.COMPLETED, response.getExportPhase().getStatus());
    assertEquals(1, response.getTransferPhase().getFiles().size());
    assertEquals(MigrationExecutionStatus.COMPLETED, response.getTransferPhase().getStatus());
    TransferFileStatusDto transferFile = response.getTransferPhase().getFiles().getFirst();
    assertEquals(42L, transferFile.getUploadId());
    assertEquals(12L, transferFile.getRecordsAccepted());
    assertEquals(3L, transferFile.getRecordsRejected());
    assertEquals(UPLOADED_AT.atZone(Constants.ZONEID).toOffsetDateTime(), transferFile.getEvidenceCollectedAt());
  }

  @Test
  void exportActionReturnsAnEmptyTransferPhase() {
    UUID migrationId = UUID.randomUUID();
    MigrationExecution execution = migrationExecution(migrationId);
    execution.setAction(MigrationAction.EXPORT);
    MigrationFileExecution fileExecution = fileExecution(migrationId, MigrationFileExecutionStatus.EXPORTED);
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdOrderByCreationDateAscIdAsc(migrationId))
      .thenReturn(List.of(fileExecution));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionIdIn(List.of(fileExecution.getId())))
      .thenReturn(List.of());

    MigrationStatusDto response = service.getMigrationStatus(migrationId);

    assertEquals(1, response.getExportPhase().getFiles().size());
    assertEquals(List.of(), response.getTransferPhase().getFiles());
  }

  @Test
  void returnsPersistedExportFileFields() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileExecution fileExecution = fileExecution(migrationId, MigrationFileExecutionStatus.EXPORTED);
    MigrationFileExecutionDetail detail = fileExecutionDetail(fileExecution.getId(), 10, 8, 700L);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS))
      .thenReturn(Optional.of(fileExecution));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(fileExecution.getId()))
      .thenReturn(List.of(detail));

    ExportFileStatusDto response = service.getExportFileStatus(migrationId, MigrationFileType.ORGANIZATIONS);

    assertEquals(MigrationFileType.ORGANIZATIONS, response.getFileType());
    assertEquals(MigrationFileExecutionStatus.EXPORTED, response.getStatus());
    assertEquals("extractor-1", response.getExtractionId());
    assertEquals("exports/organizations.zip", response.getFilePath());
    assertEquals(10L, response.getRecordCount());
    assertEquals("EXTRACTOR_ERROR", response.getErrorCode());
    assertEquals("extractor error", response.getErrorMsg());
    assertEquals(EXTRACTED_AT.atZone(Constants.ZONEID).toOffsetDateTime(), response.getExtractedAt());
  }

  @Test
  void returnsPersistedTransferFileFields() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileExecution fileExecution = fileExecution(migrationId, MigrationFileExecutionStatus.WAITING_PU);
    MigrationFileExecutionDetail detail = fileExecutionDetail(fileExecution.getId(), 10, 8, 700L);
    detail.setUpdateDate(UPLOADED_AT);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS))
      .thenReturn(Optional.of(fileExecution));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(fileExecution.getId()))
      .thenReturn(List.of(detail));

    TransferFileStatusDto response = service.getTransferFileStatus(migrationId, MigrationFileType.ORGANIZATIONS);

    assertEquals(MigrationFileType.ORGANIZATIONS, response.getFileType());
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, response.getStatus());
    assertEquals(42L, response.getUploadId());
    assertEquals(UploadsStatusEnum.UPLOADED.name(), response.getUploadStatus());
    assertNull(response.getRecordsAccepted());
    assertNull(response.getRecordsRejected());
    assertEquals("UPLOAD_ERROR", response.getErrorCode());
    assertEquals("upload error", response.getErrorMsg());
    assertEquals(UPLOADED_AT.atZone(Constants.ZONEID).toOffsetDateTime(), response.getUploadedAt());
    assertNull(response.getEvidenceCollectedAt());
  }

  @Test
  void givenPendingDetailWhenGetTransferFileStatusThenDoesNotExposeCompleteEvidenceTimestamp() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileExecution fileExecution = fileExecution(migrationId, MigrationFileExecutionStatus.WAITING_PU);
    MigrationFileExecutionDetail completedDetail = fileExecutionDetail(fileExecution.getId(), 10, 8, 700L);
    completedDetail.setUpdateDate(EXTRACTED_AT);
    MigrationFileExecutionDetail partialDetail = fileExecutionDetail(fileExecution.getId(), 5, 4, 300L);
    partialDetail.setStatus(MigrationFileExecutionDetailStatus.PARTIAL);
    partialDetail.setUpdateDate(UPLOADED_AT);
    MigrationFileExecutionDetail pendingDetail = fileExecutionDetail(fileExecution.getId(), 2, 0, 100L);
    pendingDetail.setStatus(MigrationFileExecutionDetailStatus.PENDING);
    pendingDetail.setUpdateDate(UPDATED_AT);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS))
      .thenReturn(Optional.of(fileExecution));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(fileExecution.getId()))
      .thenReturn(List.of(completedDetail, partialDetail, pendingDetail));

    TransferFileStatusDto response = service.getTransferFileStatus(migrationId, MigrationFileType.ORGANIZATIONS);

    assertNull(response.getEvidenceCollectedAt());
  }

  @Test
  void transferFileStatusReturnsSharedStatusWithoutTransferDataWhenNoUploadExists() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileExecution fileExecution = fileExecution(migrationId, MigrationFileExecutionStatus.EXPORTED);
    fileExecution.setUploadId(null);
    fileExecution.setUploadStatus(null);
    fileExecution.setUploadErrorCode(null);
    fileExecution.setUploadErrorMsg(null);
    fileExecution.setUploadCompletedAt(null);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS))
      .thenReturn(Optional.of(fileExecution));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(fileExecution.getId()))
      .thenReturn(List.of());

    TransferFileStatusDto response = service.getTransferFileStatus(migrationId, MigrationFileType.ORGANIZATIONS);

    assertEquals(MigrationFileExecutionStatus.EXPORTED, response.getStatus());
    assertNull(response.getUploadId());
    assertNull(response.getUploadStatus());
    assertNull(response.getRecordsAccepted());
    assertNull(response.getRecordsRejected());
    assertNull(response.getErrorCode());
    assertNull(response.getErrorMsg());
    assertNull(response.getUploadedAt());
    assertNull(response.getEvidenceCollectedAt());
  }

  @Test
  void unknownMigrationReturnsNotFound() {
    UUID migrationId = UUID.randomUUID();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.empty());

    ResponseStatusException exception = assertThrows(
      ResponseStatusException.class,
      () -> service.getMigrationStatus(migrationId)
    );

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
  }

  @Test
  void missingFileExecutionReturnsNotFound() {
    UUID migrationId = UUID.randomUUID();
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS))
      .thenReturn(Optional.empty());

    ResponseStatusException exception = assertThrows(
      ResponseStatusException.class,
      () -> service.getExportFileStatus(migrationId, MigrationFileType.ORGANIZATIONS)
    );

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
  }

  @Test
  void givenPuFailureWhenGetMigrationStatusThenKeepsExportCompleted() {
    UUID migrationId = UUID.randomUUID();
    MigrationExecution execution = migrationExecution(migrationId);
    execution.setStatus(MigrationExecutionStatus.FAILED);
    execution.setErrorCode("EVIDENCE_PU_FAILED");
    execution.setErrorMsg("PU error");
    MigrationFileExecution file = fileExecution(migrationId, MigrationFileExecutionStatus.FAILED);
    MigrationFileExecutionDetail detail = fileExecutionDetail(file.getId(), 10, 8, 500L);
    detail.setStatus(MigrationFileExecutionDetailStatus.PARTIAL);
    detail.setUpdateDate(UPLOADED_AT);
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdOrderByCreationDateAscIdAsc(migrationId))
      .thenReturn(List.of(file));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionIdIn(List.of(file.getId())))
      .thenReturn(List.of(detail));

    MigrationStatusDto result = service.getMigrationStatus(migrationId);

    checkNotNullFields(result);
    checkNotNullFields(result.getExportPhase());
    checkNotNullFields(result.getTransferPhase());
    assertEquals(MigrationExecutionStatus.FAILED, result.getStatus());
    assertEquals(MigrationExecutionStatus.COMPLETED, result.getExportPhase().getStatus());
    assertEquals(MigrationExecutionStatus.FAILED, result.getTransferPhase().getStatus());
    assertEquals(MigrationFileExecutionStatus.EXPORTED, result.getExportPhase().getFiles().getFirst().getStatus());
    verify(migrationExecutionRepositoryMock).findById(migrationId);
    verify(migrationFileExecutionRepositoryMock).findAllByMigrationIdOrderByCreationDateAscIdAsc(migrationId);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionIdIn(List.of(file.getId()));
  }

  @Test
  void givenWaitingFileWithTerminalDetailWhenGetTransferFileStatusThenDoesNotExposeCompleteEvidenceTimestamp() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileExecution file = fileExecution(migrationId, MigrationFileExecutionStatus.WAITING_PU);
    MigrationFileExecutionDetail detail = fileExecutionDetail(file.getId(), 10, 8, 500L);
    detail.setStatus(MigrationFileExecutionDetailStatus.PARTIAL);
    detail.setUpdateDate(UPLOADED_AT);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS))
      .thenReturn(Optional.of(file));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(file.getId()))
      .thenReturn(List.of(detail));

    TransferFileStatusDto result = service.getTransferFileStatus(migrationId, MigrationFileType.ORGANIZATIONS);

    checkNotNullFields(result, "evidenceCollectedAt", "recordsAccepted", "recordsRejected");
    assertNull(result.getEvidenceCollectedAt());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(file.getId());
  }

  @Test
  void givenPartialEvidenceWhenGetTransferFileStatusThenMapsCountsAndCompleteTimestamp() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileExecution file = fileExecution(migrationId, MigrationFileExecutionStatus.WARNING);
    file.setUploadStatus(UploadsStatusEnum.COMPLETED);
    MigrationFileExecutionDetail detail = fileExecutionDetail(file.getId(), 10, 8, 500L);
    detail.setStatus(MigrationFileExecutionDetailStatus.PARTIAL);
    detail.setUpdateDate(UPLOADED_AT);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS))
      .thenReturn(Optional.of(file));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(file.getId()))
      .thenReturn(List.of(detail));

    TransferFileStatusDto result = service.getTransferFileStatus(migrationId, MigrationFileType.ORGANIZATIONS);

    checkNotNullFields(result);
    assertEquals(8L, result.getRecordsAccepted());
    assertEquals(2L, result.getRecordsRejected());
    assertNotNull(result.getEvidenceCollectedAt());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(file.getId());
  }

  @Test
  void givenBlockedDescendantsWhenGetMigrationStatusThenDoesNotReportAnActivePhaseForever() {
    UUID migrationId = UUID.randomUUID();
    MigrationExecution execution = migrationExecution(migrationId);
    execution.setStatus(MigrationExecutionStatus.FAILED);
    execution.setErrorCode("EVIDENCE_PU_FAILED");
    execution.setErrorMsg("PU error");
    execution.setFileTypes(List.of(MigrationFileType.ORGANIZATIONS, MigrationFileType.ORG_SIL_SERVICES));
    MigrationFileExecution file = fileExecution(migrationId, MigrationFileExecutionStatus.FAILED);
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdOrderByCreationDateAscIdAsc(migrationId))
      .thenReturn(List.of(file));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionIdIn(List.of(file.getId())))
      .thenReturn(List.of());

    MigrationStatusDto result = service.getMigrationStatus(migrationId);

    checkNotNullFields(result);
    assertEquals(MigrationExecutionStatus.FAILED, result.getExportPhase().getStatus());
    assertEquals(MigrationExecutionStatus.FAILED, result.getTransferPhase().getStatus());
    verify(migrationExecutionRepositoryMock).findById(migrationId);
    verify(migrationFileExecutionRepositoryMock).findAllByMigrationIdOrderByCreationDateAscIdAsc(migrationId);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionIdIn(List.of(file.getId()));
  }

  @Test
  void givenTimeoutWithProgressiveCountsWhenGetTransferFileStatusThenDoesNotExposeFinalEvidence() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileExecution file = fileExecution(migrationId, MigrationFileExecutionStatus.FAILED);
    file.setUploadStatus(UploadsStatusEnum.PROCESSING);
    file.setUploadErrorCode("EVIDENCE_TIMEOUT");
    MigrationFileExecutionDetail detail = fileExecutionDetail(file.getId(), 10, 3, 500L);
    detail.setStatus(MigrationFileExecutionDetailStatus.FAILED);
    detail.setUpdateDate(UPLOADED_AT);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS))
      .thenReturn(Optional.of(file));
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(file.getId()))
      .thenReturn(List.of(detail));

    TransferFileStatusDto result = service.getTransferFileStatus(migrationId, MigrationFileType.ORGANIZATIONS);

    checkNotNullFields(result, "recordsAccepted", "recordsRejected", "evidenceCollectedAt");
    assertEquals(MigrationFileExecutionStatus.FAILED, result.getStatus());
    assertEquals("EVIDENCE_TIMEOUT", result.getErrorCode());
    assertNull(result.getRecordsAccepted());
    assertNull(result.getRecordsRejected());
    assertNull(result.getEvidenceCollectedAt());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(migrationId, MigrationFileType.ORGANIZATIONS);
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(file.getId());
  }

  private MigrationExecution migrationExecution(UUID migrationId) {
    return MigrationExecution.builder()
      .id(migrationId)
      .action(MigrationAction.COMPLETE)
      .status(MigrationExecutionStatus.RUNNING)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipacodes(List.of("80000000000"))
      .fileTypes(List.of(MigrationFileType.ORGANIZATIONS))
      .creationDate(CREATED_AT)
      .updateDate(UPDATED_AT)
      .errorCode("MIGRATION_ERROR")
      .errorMsg("migration error")
      .build();
  }

  private MigrationFileExecution fileExecution(UUID migrationId, MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder()
      .migrationId(migrationId)
      .id(UUID.randomUUID())
      .fileType(MigrationFileType.ORGANIZATIONS)
      .status(status)
      .extractionId("extractor-1")
      .filePath("exports/organizations.zip")
      .extractionErrorCode("EXTRACTOR_ERROR")
      .extractionErrorMsg("extractor error")
      .uploadId(42L)
      .uploadStatus(UploadsStatusEnum.UPLOADED)
      .uploadErrorCode("UPLOAD_ERROR")
      .uploadErrorMsg("upload error")
      .extractionCompletedAt(EXTRACTED_AT)
      .uploadCompletedAt(UPLOADED_AT)
      .build();
  }

  private MigrationFileExecutionDetail fileExecutionDetail(
    UUID fileExecutionId,
    Integer totalRows,
    Integer correctlyImportedRows,
    Long fileSize
  ) {
    return MigrationFileExecutionDetail.builder()
      .migrationFileExecutionId(fileExecutionId)
      .status(MigrationFileExecutionDetailStatus.COMPLETED)
      .fileName("organizations.csv")
      .fileSize(fileSize)
      .numTotalRows(totalRows)
      .numCorrectlyImportedRows(correctlyImportedRows)
      .build();
  }
}
