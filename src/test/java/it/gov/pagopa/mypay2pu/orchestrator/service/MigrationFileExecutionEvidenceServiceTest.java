package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationFileExecutionDetailMapper;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionEvidenceValidator;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationFileExecutionEvidenceServiceTest {

  private static final UUID FILE_ID = UUID.fromString("6b051eb1-2be2-414f-9f4e-91de6dc02b1a");
  private static final LocalDateTime ACCEPTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 18, 12, 0);
  @Mock private MigrationFileExecutionRepository fileRepositoryMock;
  @Mock private MigrationFileExecutionDetailRepository detailRepositoryMock;
  @Captor private ArgumentCaptor<List<MigrationFileExecutionDetail>> detailsCaptor;
  private MigrationFileExecutionEvidenceService service;
  private MigrationFileExecution file;

  @BeforeEach
  void setUp() {
    service = new MigrationFileExecutionEvidenceService(fileRepositoryMock, detailRepositoryMock,
      new MigrationFileExecutionDetailMapper(), new MigrationFileExecutionEvidenceValidator());
    file = MigrationFileExecution.builder().id(FILE_ID).migrationId(UUID.randomUUID())
      .fileType(MigrationFileType.ORGANIZATIONS).status(WAITING_PU)
      .uploadId(42L).uploadStatus(UploadsStatusEnum.UPLOADED).uploadCompletedAt(ACCEPTED_AT)
      .extractionId("extraction").filePath("organizations.zip")
      .extractionStartedAt(ACCEPTED_AT.minusHours(1)).extractionCompletedAt(ACCEPTED_AT.minusMinutes(1)).build();
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(fileRepositoryMock, detailRepositoryMock);
  }

  @ParameterizedTest
  @MethodSource("outcomes")
  void givenPuOutcomeWhenRecordEvidenceThenPersistsExpectedState(
    UploadsStatusEnum uploadStatus, IngestionFlowFileStatus detailStatus,
    Long total, Long accepted, MigrationFileExecutionStatus expected
  ) {
    prepare(List.of());
    when(fileRepositoryMock.save(file)).thenReturn(file);
    UploadDetails detail = detail(detailStatus, total, accepted);

    assertSame(file, service.recordEvidence(FILE_ID, 42L, upload(uploadStatus), List.of(detail)));

    assertEquals(expected, file.getStatus());
    assertEquals(uploadStatus, file.getUploadStatus());
    assertEquals("extraction", file.getExtractionId());
    assertEquals("organizations.zip", file.getFilePath());
    assertEquals(ACCEPTED_AT, file.getUploadCompletedAt());
    assertEquals(ACCEPTED_AT.minusMinutes(1), file.getExtractionCompletedAt());
    if (expected == FAILED) {
      assertEquals("EVIDENCE_PU_FAILED", file.getUploadErrorCode());
    }
    verifyReads();
    verify(detailRepositoryMock).saveAll(detailsCaptor.capture());
    assertEquals(1, detailsCaptor.getValue().size());
    assertEquals("organizations.errors.csv", detailsCaptor.getValue().getFirst().getDiscardFileName());
    verify(fileRepositoryMock).save(file);
  }

  static Stream<Arguments> outcomes() {
    return Stream.of(
      Arguments.of(UploadsStatusEnum.UPLOADED, IngestionFlowFileStatus.UPLOADED, null, null, WAITING_PU),
      Arguments.of(UploadsStatusEnum.PROCESSING, IngestionFlowFileStatus.PROCESSING, 10L, 3L, WAITING_PU),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.WAITING_FILE, null, null, WAITING_PU),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.COMPLETED, 10L, 10L, COMPLETED),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.WARNING, 10L, 8L, WARNING),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.WARNING, 10L, 0L, FAILED),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.COMPLETED, 0L, 0L, COMPLETED),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.COMPLETED, null, null, WAITING_PU),
      Arguments.of(UploadsStatusEnum.ERROR, IngestionFlowFileStatus.ERROR, null, null, FAILED),
      Arguments.of(UploadsStatusEnum.PROCESSING, IngestionFlowFileStatus.ERROR, 10L, 2L, FAILED)
    );
  }

  @Test
  void givenExistingDetailWhenRecordEvidenceThenUpdatesWithoutReplacingLocalId() {
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.PROCESSING, null, null);
    original.setId(UUID.randomUUID());
    original.setCreationDate(ACCEPTED_AT);
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordEvidence(FILE_ID, 42L, upload(UploadsStatusEnum.COMPLETED),
      List.of(detail(IngestionFlowFileStatus.WARNING, 10L, 8L)));

    verifyReads();
    verify(detailRepositoryMock).saveAll(detailsCaptor.capture());
    MigrationFileExecutionDetail persisted = detailsCaptor.getValue().getFirst();
    assertEquals(original.getId(), persisted.getId());
    assertEquals(original.getCreationDate(), persisted.getCreationDate());
    assertEquals(MigrationFileExecutionDetailStatus.PARTIAL, persisted.getStatus());
    assertEquals(8, persisted.getNumCorrectlyImportedRows());
    verify(fileRepositoryMock).save(file);
  }

  @ParameterizedTest
  @MethodSource("incompleteSnapshots")
  void givenIncompleteSnapshotWhenRecordEvidenceThenRemainsWaiting(
    List<UploadDetails> details, Integer expectedCount
  ) {
    prepare(List.of());
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordEvidence(FILE_ID, 42L, upload(UploadsStatusEnum.COMPLETED).numTotalFiles(expectedCount), details);

    assertEquals(WAITING_PU, file.getStatus());
    verifyReads();
    if (details != null && !details.isEmpty()) {
      verify(detailRepositoryMock).saveAll(detailsCaptor.capture());
    }
    verify(fileRepositoryMock).save(file);
  }

  static Stream<Arguments> incompleteSnapshots() {
    return Stream.of(Arguments.of(null, null), Arguments.of(List.of(), 0),
      Arguments.of(List.of(detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)), 2));
  }

  @Test
  void givenTerminalReplayWhenRecordEvidenceThenDoesNotWrite() {
    file.setStatus(COMPLETED);
    file.setUploadStatus(UploadsStatusEnum.COMPLETED);
    prepare(List.of(localDetail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)));

    assertSame(file, service.recordEvidence(FILE_ID, 42L, upload(UploadsStatusEnum.COMPLETED),
      List.of(detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L))));

    verifyReads();
  }

  @Test
  void givenLateProcessingSnapshotWhenRecordEvidenceThenRejectsWithoutRegressingTerminalFile() {
    file.setStatus(COMPLETED);
    file.setUploadStatus(UploadsStatusEnum.COMPLETED);
    prepare(List.of(localDetail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)));
    Uploads upload = upload(UploadsStatusEnum.PROCESSING);
    List<UploadDetails> details = List.of(detail(IngestionFlowFileStatus.PROCESSING, 10L, 3L));

    assertThrows(ConflictException.class, () -> service.recordEvidence(FILE_ID, 42L,
      upload, details));

    assertEquals(COMPLETED, file.getStatus());
    verifyReads();
  }

  @Test
  void givenDifferentDetailIdsWhenRecordEvidenceThenDoesNotTreatPartialSnapshotAsComplete() {
    prepare(List.of(localDetail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)));
    when(fileRepositoryMock.save(file)).thenReturn(file);
    UploadDetails different = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L).uploadDetailId(124L);

    service.recordEvidence(FILE_ID, 42L, upload(UploadsStatusEnum.COMPLETED), List.of(different));

    assertEquals(WAITING_PU, file.getStatus());
    verifyReads();
    verify(detailRepositoryMock).saveAll(detailsCaptor.capture());
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenDuplicateDetailWhenRecordEvidenceThenRejectsBeforeReadingDetails() {
    when(fileRepositoryMock.findByIdForUpdate(FILE_ID)).thenReturn(Optional.of(file));
    UploadDetails detail = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of(detail, detail);

    assertThrows(InvalidValueException.class, () -> service.recordEvidence(FILE_ID, 42L,
      upload, details));

    assertEquals(WAITING_PU, file.getStatus());
    verify(fileRepositoryMock).findByIdForUpdate(FILE_ID);
  }

  @Test
  void givenDifferentUploadWhenRecordEvidenceThenRejectsBeforeReadingDetails() {
    when(fileRepositoryMock.findByIdForUpdate(FILE_ID)).thenReturn(Optional.of(file));
    Uploads upload = upload(UploadsStatusEnum.COMPLETED).uploadId(43L);
    List<UploadDetails> details = List.of();

    assertThrows(InvalidValueException.class, () -> service.recordEvidence(FILE_ID, 42L,
      upload, details));

    verify(fileRepositoryMock).findByIdForUpdate(FILE_ID);
  }

  @ParameterizedTest
  @MethodSource("invalidCounts")
  void givenInvalidCountsWhenRecordEvidenceThenRejectsWithoutSaving(Long total, Long accepted) {
    prepare(List.of());
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of(detail(IngestionFlowFileStatus.COMPLETED, total, accepted));

    assertThrows(InvalidValueException.class, () -> service.recordEvidence(FILE_ID, 42L,
      upload, details));

    assertEquals(WAITING_PU, file.getStatus());
    verifyReads();
  }

  static Stream<Arguments> invalidCounts() {
    return Stream.of(Arguments.of(-1L, 0L), Arguments.of(1L, -1L),
      Arguments.of(1L, 2L), Arguments.of(Long.MAX_VALUE, 0L));
  }

  @Test
  void givenMoreDetailsThanExpectedWhenRecordEvidenceThenRejectsWithoutSaving() {
    prepare(List.of());
    UploadDetails first = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    UploadDetails second = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L).uploadDetailId(124L);
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of(first, second);

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> service.recordEvidence(FILE_ID, 42L, upload, details));

    assertEquals("EVIDENCE_INVALID_RESPONSE", failure.getCode());
    assertEquals(WAITING_PU, file.getStatus());
    verifyReads();
  }

  @Test
  void givenMissingFileWhenRecordEvidenceThenRejectsBeforeReadingDetails() {
    when(fileRepositoryMock.findByIdForUpdate(FILE_ID)).thenReturn(Optional.empty());
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of();

    NotFoundException failure = assertThrows(NotFoundException.class,
      () -> service.recordEvidence(FILE_ID, 42L, upload, details));

    assertEquals("MIGRATION_FILE_EXECUTION_NOT_FOUND", failure.getCode());
    verify(fileRepositoryMock).findByIdForUpdate(FILE_ID);
  }

  @Test
  void givenTimeoutWhenMarkFailedThenPreservesPuStatusAndExtractionFields() {
    when(fileRepositoryMock.findByIdForUpdate(FILE_ID)).thenReturn(Optional.of(file));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.markFailed(FILE_ID, 42L, "EVIDENCE_TIMEOUT", "timed out");

    assertEquals(FAILED, file.getStatus());
    assertEquals("EVIDENCE_TIMEOUT", file.getUploadErrorCode());
    assertEquals(UploadsStatusEnum.UPLOADED, file.getUploadStatus());
    assertNull(file.getExtractionErrorCode());
    assertEquals("extraction", file.getExtractionId());
    verify(fileRepositoryMock).findByIdForUpdate(FILE_ID);
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenConcurrentCompletionWhenMarkFailedThenDoesNotOverwriteTerminalFile() {
    file.setStatus(COMPLETED);
    when(fileRepositoryMock.findByIdForUpdate(FILE_ID)).thenReturn(Optional.of(file));

    assertSame(file, service.markFailed(FILE_ID, 42L, "EVIDENCE_TIMEOUT", "timed out"));

    assertEquals(COMPLETED, file.getStatus());
    verify(fileRepositoryMock).findByIdForUpdate(FILE_ID);
  }

  private void prepare(List<MigrationFileExecutionDetail> existing) {
    when(fileRepositoryMock.findByIdForUpdate(FILE_ID)).thenReturn(Optional.of(file));
    when(detailRepositoryMock.findAllByMigrationFileExecutionId(FILE_ID)).thenReturn(existing);
  }

  private void verifyReads() {
    verify(fileRepositoryMock).findByIdForUpdate(FILE_ID);
    verify(detailRepositoryMock).findAllByMigrationFileExecutionId(FILE_ID);
  }

  private MigrationFileExecutionDetail localDetail(IngestionFlowFileStatus status, Long total, Long accepted) {
    return new MigrationFileExecutionDetailMapper().toMigrationFileExecutionDetail(FILE_ID, 42L,
      detail(status, total, accepted));
  }

  private static Uploads upload(UploadsStatusEnum status) {
    return new Uploads().uploadId(42L).status(status).numTotalFiles(1);
  }

  private static UploadDetails detail(IngestionFlowFileStatus status, Long total, Long accepted) {
    return new UploadDetails().uploadId(42L).uploadDetailId(123L).fileName("organizations.csv")
      .fileSize(500L).status(status).numTotalRows(total).numCorrectlyImportedRows(accepted)
      .discardFileName("organizations.errors.csv");
  }
}
