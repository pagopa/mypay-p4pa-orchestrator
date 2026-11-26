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
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferOutcomeValidator;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationFileExecutionTransferOutcomeServiceTest {

  private static final UUID FILE_ID = UUID.fromString("6b051eb1-2be2-414f-9f4e-91de6dc02b1a");
  private static final UUID DETAIL_ID = UUID.fromString("436bd723-d14b-4efb-950d-1189177bf5e7");
  private static final String FILE_NAME = "IPA_TEST_2-organizations.csv";
  private static final LocalDateTime ACCEPTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 18, 12, 0);
  @Mock private MigrationFileExecutionRepository fileRepositoryMock;
  @Mock private MigrationFileExecutionDetailRepository detailRepositoryMock;
  private MigrationFileExecutionTransferOutcomeService service;
  private MigrationFileExecution file;

  @BeforeEach
  void setUp() {
    service = new MigrationFileExecutionTransferOutcomeService(fileRepositoryMock, detailRepositoryMock,
      new MigrationFileExecutionDetailMapper(), new MigrationFileExecutionTransferOutcomeValidator());
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
  void givenPuOutcomeWhenRecordTransferOutcomeThenPersistsExpectedState(
    UploadsStatusEnum uploadStatus, IngestionFlowFileStatus detailStatus,
    Long total, Long accepted, MigrationFileExecutionStatus expected
  ) {
    MigrationFileExecutionDetail original = exportedDetail();
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);
    UploadDetails detail = detail(detailStatus, total, accepted);

    assertSame(file, service.recordTransferOutcome(FILE_ID, upload(uploadStatus), List.of(detail)));

    assertEquals(expected, file.getStatus());
    assertEquals(uploadStatus, file.getUploadStatus());
    assertEquals(total, original.getNumTotalRows());
    assertEquals(accepted, original.getNumCorrectlyImportedRows());
    assertEquals("extraction", file.getExtractionId());
    assertEquals("organizations.zip", file.getFilePath());
    assertEquals(ACCEPTED_AT, file.getUploadCompletedAt());
    assertEquals(ACCEPTED_AT.minusMinutes(1), file.getExtractionCompletedAt());
    if (expected == FAILED) {
      assertEquals("TRANSFER_OUTCOME_PU_FAILED", file.getUploadErrorCode());
    }
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(original));
    assertEquals("organizations.errors.csv", original.getDiscardFileName());
    if (expected == WAITING_PU) {
      assertEquals(detailStatus == IngestionFlowFileStatus.ERROR
        ? MigrationFileExecutionDetailStatus.FAILED : MigrationFileExecutionDetailStatus.WAITING_PU,
        original.getStatus());
    }
    assertEquals(123L, original.getUploadDetailId());
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
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.COMPLETED,
        (long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE, COMPLETED),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.COMPLETED,
        (long) Integer.MAX_VALUE + 1, (long) Integer.MAX_VALUE + 1, COMPLETED),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.COMPLETED,
        Long.MAX_VALUE, Long.MAX_VALUE, COMPLETED),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.COMPLETED, null, null, COMPLETED),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.WARNING, 10L, null, FAILED),
      Arguments.of(UploadsStatusEnum.COMPLETED, IngestionFlowFileStatus.COMPLETED, null, 0L, COMPLETED),
      Arguments.of(UploadsStatusEnum.ERROR, IngestionFlowFileStatus.ERROR, null, null, FAILED),
      Arguments.of(UploadsStatusEnum.PROCESSING, IngestionFlowFileStatus.ERROR, 10L, 2L, WAITING_PU)
    );
  }

  @Test
  void givenExistingDetailWhenRecordTransferOutcomeThenUpdatesWithoutReplacingLocalId() {
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.PROCESSING, null, null);
    UUID detailId = UUID.randomUUID();
    original.setId(detailId);
    original.setCreationDate(ACCEPTED_AT);
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED),
      List.of(detail(IngestionFlowFileStatus.WARNING, 10L, 8L)));

    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(original));
    assertEquals(detailId, original.getId());
    assertEquals(ACCEPTED_AT, original.getCreationDate());
    assertEquals(MigrationFileExecutionDetailStatus.PARTIAL, original.getStatus());
    assertEquals(8L, original.getNumCorrectlyImportedRows());
    verify(fileRepositoryMock).save(file);
  }

  @ParameterizedTest
  @MethodSource("missingCounts")
  void givenPreviousCountsWhenRecordTransferOutcomeThenUsesOnlyCurrentCounts(
    Long total, Long accepted, MigrationFileExecutionStatus expected
  ) {
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.PROCESSING, 10L, 3L);
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED),
      List.of(detail(IngestionFlowFileStatus.COMPLETED, total, accepted)));

    assertEquals(expected, file.getStatus());
    assertEquals(total, original.getNumTotalRows());
    assertEquals(accepted, original.getNumCorrectlyImportedRows());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(original));
    verify(fileRepositoryMock).save(file);
  }

  static Stream<Arguments> missingCounts() {
    return Stream.of(
      Arguments.of(null, null, COMPLETED),
      Arguments.of(10L, null, FAILED),
      Arguments.of(null, 0L, COMPLETED)
    );
  }

  @ParameterizedTest
  @MethodSource("incompleteSnapshots")
  void givenIncompleteSnapshotWhenRecordTransferOutcomeThenRemainsWaiting(
    List<UploadDetails> details, Integer expectedCount
  ) {
    MigrationFileExecutionDetail original = exportedDetail();
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED).numTotalFiles(expectedCount), details);

    assertEquals(WAITING_PU, file.getStatus());
    verifyReads();
    if (details != null && !details.isEmpty()) {
      verify(detailRepositoryMock).saveAll(List.of(original));
    }
    verify(fileRepositoryMock).save(file);
  }

  static Stream<Arguments> incompleteSnapshots() {
    return Stream.of(Arguments.of(null, null), Arguments.of(List.of(), 0),
      Arguments.of(List.of(detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)), 2));
  }

  @Test
  void givenTerminalReplayWhenRecordTransferOutcomeThenDoesNotWrite() {
    file.setStatus(COMPLETED);
    file.setUploadStatus(UploadsStatusEnum.COMPLETED);
    prepare(List.of(localDetail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)));

    assertSame(file, service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED),
      List.of(detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L))));

    verifyReads();
  }

  @Test
  void givenLateProcessingSnapshotWhenRecordTransferOutcomeThenRejectsWithoutRegressingTerminalFile() {
    file.setStatus(COMPLETED);
    file.setUploadStatus(UploadsStatusEnum.COMPLETED);
    prepare(List.of(localDetail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)));
    Uploads upload = upload(UploadsStatusEnum.PROCESSING);
    List<UploadDetails> details = List.of(detail(IngestionFlowFileStatus.PROCESSING, 10L, 3L));

    assertThrows(ConflictException.class, () -> service.recordTransferOutcome(FILE_ID,
      upload, details));

    assertEquals(COMPLETED, file.getStatus());
    verifyReads();
  }

  @Test
  void givenDifferentDetailIdWhenRecordTransferOutcomeThenUpdatesTheDetailMatchedByFileName() {
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    UUID originalId = original.getId();
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);
    UploadDetails different = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L).uploadDetailId(124L);
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of(different);

    assertSame(file, service.recordTransferOutcome(FILE_ID, upload, details));

    assertEquals(COMPLETED, file.getStatus());
    assertEquals(originalId, original.getId());
    assertEquals(124L, original.getUploadDetailId());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(original));
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenDifferentFileSizeWhenRecordTransferOutcomeThenUpdatesThePreviouslyAcquiredSize() {
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.PROCESSING, 10L, 3L);
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED),
      List.of(detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L).fileSize(501L)));

    assertEquals(COMPLETED, file.getStatus());
    assertEquals(501L, original.getFileSize());
    assertEquals(10L, original.getNumCorrectlyImportedRows());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(original));
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenDuplicateDetailWhenRecordTransferOutcomeThenRejectsDuringCorrelation() {
    MigrationFileExecutionDetail original = exportedDetail();
    prepare(List.of(original));
    UploadDetails detail = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of(detail, detail);

    assertThrows(IllegalStateException.class, () -> service.recordTransferOutcome(FILE_ID,
      upload, details));

    assertEquals(WAITING_PU, file.getStatus());
    assertNull(original.getUploadDetailId());
    verifyReads();
  }

  @Test
  void givenDuplicateIdentifierForDifferentFilesWhenRecordTransferOutcomeThenRejectsWithoutBindingEitherRow() {
    MigrationFileExecutionDetail first = exportedDetail();
    MigrationFileExecutionDetail second = exportedDetail("IPA_TEST_2-other.csv");
    prepare(List.of(first, second));
    Uploads upload = upload(UploadsStatusEnum.COMPLETED).numTotalFiles(2);
    UploadDetails firstResult = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    UploadDetails secondResult = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)
      .fileName(second.getFileName());
    List<UploadDetails> snapshot = List.of(firstResult, secondResult);

    assertThrows(IllegalStateException.class,
      () -> service.recordTransferOutcome(FILE_ID, upload, snapshot));

    assertNull(first.getUploadDetailId());
    assertNull(second.getUploadDetailId());
    assertEquals(WAITING_PU, file.getStatus());
    verifyReads();
  }

  @Test
  void givenDifferentUploadWhenRecordTransferOutcomeThenRejectsBeforeReadingDetails() {
    when(fileRepositoryMock.findById(FILE_ID)).thenReturn(Optional.of(file));
    Uploads upload = upload(UploadsStatusEnum.COMPLETED).uploadId(43L);
    List<UploadDetails> details = List.of();

    assertThrows(InvalidValueException.class, () -> service.recordTransferOutcome(FILE_ID,
      upload, details));

    verify(fileRepositoryMock).findById(FILE_ID);
  }

  @Test
  void givenMissingFileWhenRecordTransferOutcomeThenRejectsBeforeReadingDetails() {
    when(fileRepositoryMock.findById(FILE_ID)).thenReturn(Optional.empty());
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of();

    NotFoundException failure = assertThrows(NotFoundException.class,
      () -> service.recordTransferOutcome(FILE_ID, upload, details));

    assertEquals("MIGRATION_FILE_EXECUTION_NOT_FOUND", failure.getCode());
    verify(fileRepositoryMock).findById(FILE_ID);
  }

  @Test
  void givenTimeoutWhenMarkFailedThenPreservesPuStatusAndExtractionFields() {
    when(fileRepositoryMock.findById(FILE_ID)).thenReturn(Optional.of(file));
    when(detailRepositoryMock.findAllByMigrationFileExecutionId(FILE_ID)).thenReturn(List.of());
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.markFailed(FILE_ID, "TRANSFER_OUTCOME_TIMEOUT", "timed out");

    assertEquals(FAILED, file.getStatus());
    assertEquals("TRANSFER_OUTCOME_TIMEOUT", file.getUploadErrorCode());
    assertEquals(UploadsStatusEnum.UPLOADED, file.getUploadStatus());
    assertNull(file.getExtractionErrorCode());
    assertEquals("extraction", file.getExtractionId());
    verify(fileRepositoryMock).findById(FILE_ID);
    verify(detailRepositoryMock).findAllByMigrationFileExecutionId(FILE_ID);
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenCompletedFileWhenMarkFailedThenDoesNotOverwriteTerminalFile() {
    file.setStatus(COMPLETED);
    when(fileRepositoryMock.findById(FILE_ID)).thenReturn(Optional.of(file));

    assertSame(file, service.markFailed(FILE_ID, "TRANSFER_OUTCOME_TIMEOUT", "timed out"));

    assertEquals(COMPLETED, file.getStatus());
    verify(fileRepositoryMock).findById(FILE_ID);
  }

  @Test
  void givenMultipleExportedDetailsWhenRecordTransferOutcomeThenCorrelatesByNameAndPreservesEachIdentity() {
    MigrationFileExecutionDetail first = exportedDetail();
    MigrationFileExecutionDetail second = exportedDetail("IPA_TEST_3-organizations.csv");
    prepare(List.of(first, second));
    when(fileRepositoryMock.save(file)).thenReturn(file);
    UploadDetails firstSnapshot = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    UploadDetails secondSnapshot = detail(IngestionFlowFileStatus.WARNING, 10L, 8L)
      .fileName(second.getFileName()).uploadDetailId(124L);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED).numTotalFiles(2),
      List.of(secondSnapshot, firstSnapshot));

    assertEquals(WARNING, file.getStatus());
    verifyReads();
    List<MigrationFileExecutionDetail> saved = List.of(first, second);
    verify(detailRepositoryMock).saveAll(saved);
    assertEquals(DETAIL_ID, first.getId());
    assertEquals(List.of("IPA_TEST_2", "IPA_TEST_3"),
      saved.stream().map(MigrationFileExecutionDetail::getIpaCode).toList());
    assertEquals(List.of(123L, 124L), saved.stream().map(MigrationFileExecutionDetail::getUploadDetailId).toList());
    assertEquals(ACCEPTED_AT.minusHours(1), first.getCreationDate());
    assertEquals(ACCEPTED_AT.minusHours(1), second.getCreationDate());
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenPartialSnapshotWhenRecordTransferOutcomeThenLeavesMissingExportedDetailsWaiting() {
    MigrationFileExecutionDetail first = exportedDetail();
    MigrationFileExecutionDetail second = exportedDetail("IPA_TEST_3-organizations.csv");
    prepare(List.of(first, second));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED).numTotalFiles(2),
      List.of(detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)));

    assertEquals(WAITING_PU, file.getStatus());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(first, second));
    assertEquals(123L, first.getUploadDetailId());
    assertEquals(MigrationFileExecutionDetailStatus.COMPLETED, first.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, second.getStatus());
    assertNull(second.getUploadDetailId());
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenUnknownPuFileWhenRecordTransferOutcomeThenRejectsWithoutCreatingDetails() {
    prepare(List.of(exportedDetail()));
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of(detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)
      .fileName("IPA_TEST_3-unknown.csv"));

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> service.recordTransferOutcome(FILE_ID, upload, details));

    assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    assertEquals(WAITING_PU, file.getStatus());
    verifyReads();
  }

  @Test
  void givenUnknownLaterFileNameWhenRecordTransferOutcomeThenDoesNotMutateEarlierDetails() {
    MigrationFileExecutionDetail first = exportedDetail();
    MigrationFileExecutionDetail second = exportedDetail("IPA_TEST_3-organizations.csv");
    prepare(List.of(first, second));
    Uploads upload = upload(UploadsStatusEnum.COMPLETED).numTotalFiles(2);
    List<UploadDetails> details = List.of(
      detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L),
      detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)
        .uploadDetailId(124L).fileName("IPA_TEST_4-organizations.csv")
    );

    assertThrows(InvalidValueException.class, () -> service.recordTransferOutcome(FILE_ID, upload, details));

    assertEquals(WAITING_PU, file.getStatus());
    assertEquals(UploadsStatusEnum.UPLOADED, file.getUploadStatus());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, first.getStatus());
    assertNull(first.getUploadDetailId());
    assertNull(first.getNumTotalRows());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, second.getStatus());
    assertNull(second.getUploadDetailId());
    verifyReads();
  }

  @Test
  void givenEnrichedTerminalReplayWhenRecordTransferOutcomeThenKeepsTheStoredMetadata() {
    file.setStatus(COMPLETED);
    file.setUploadStatus(UploadsStatusEnum.COMPLETED);
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    prepare(List.of(original));
    Uploads upload = upload(UploadsStatusEnum.COMPLETED);
    List<UploadDetails> details = List.of(
      detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L).errorDescription("Late metadata")
    );

    assertSame(file, service.recordTransferOutcome(FILE_ID, upload, details));

    assertEquals(COMPLETED, file.getStatus());
    assertNull(original.getErrorDescription());
    verifyReads();
  }

  @Test
  void givenPuUploadErrorWhenRecordTransferOutcomeThenFailsWaitingDetailsAndPreservesCompletedOnes() {
    MigrationFileExecutionDetail waiting = exportedDetail();
    MigrationFileExecutionDetail completed = exportedDetail("IPA_TEST_3-organizations.csv");
    completed.setStatus(MigrationFileExecutionDetailStatus.COMPLETED);
    completed.setNumTotalRows(10L);
    completed.setNumCorrectlyImportedRows(10L);
    prepare(List.of(waiting, completed));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.ERROR), List.of());

    assertEquals(FAILED, file.getStatus());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(waiting, completed));
    assertEquals(DETAIL_ID, waiting.getId());
    assertEquals("IPA_TEST_2", waiting.getIpaCode());
    assertEquals(MigrationFileExecutionDetailStatus.FAILED, waiting.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.COMPLETED, completed.getStatus());
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenWaitingDetailsWhenMarkFailedThenFailsOnlyUnfinishedDetails() {
    MigrationFileExecutionDetail waiting = exportedDetail();
    MigrationFileExecutionDetail partial = exportedDetail("IPA_TEST_3-organizations.csv");
    partial.setStatus(MigrationFileExecutionDetailStatus.PARTIAL);
    partial.setNumTotalRows(10L);
    partial.setNumCorrectlyImportedRows(8L);
    prepare(List.of(waiting, partial));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.markFailed(FILE_ID, "TRANSFER_OUTCOME_TIMEOUT", "timed out");

    assertEquals(FAILED, file.getStatus());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(waiting, partial));
    assertEquals(DETAIL_ID, waiting.getId());
    assertEquals(MigrationFileExecutionDetailStatus.FAILED, waiting.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.PARTIAL, partial.getStatus());
    verify(fileRepositoryMock).save(file);
  }

  @ParameterizedTest
  @MethodSource("incrementalOutcomes")
  void givenMixedProcessingDetailsWhenRecordTransferOutcomeThenPersistsObservationsUntilUploadCompletes(
    IngestionFlowFileStatus finalStatus, Long accepted,
    MigrationFileExecutionDetailStatus expectedDetailStatus, MigrationFileExecutionStatus expectedFileStatus
  ) {
    MigrationFileExecutionDetail first = exportedDetail();
    MigrationFileExecutionDetail second = exportedDetail("IPA_TEST_3-organizations.csv");
    prepare(List.of(first, second));
    when(fileRepositoryMock.save(file)).thenReturn(file);
    UploadDetails finalDetail = detail(finalStatus, 10L, accepted);
    UploadDetails processing = detail(IngestionFlowFileStatus.PROCESSING, 10L, 3L)
      .fileName(second.getFileName()).uploadDetailId(124L);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.PROCESSING).numTotalFiles(2),
      List.of(finalDetail, processing));

    assertEquals(WAITING_PU, file.getStatus());
    assertEquals(UploadsStatusEnum.PROCESSING, file.getUploadStatus());
    assertNull(file.getUploadErrorCode());
    assertEquals(expectedDetailStatus, first.getStatus());
    assertEquals(123L, first.getUploadDetailId());
    assertEquals(accepted, first.getNumCorrectlyImportedRows());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, second.getStatus());
    assertEquals(124L, second.getUploadDetailId());
    assertEquals(10L, second.getNumTotalRows());
    assertEquals(3L, second.getNumCorrectlyImportedRows());
    assertEquals("organizations.errors.csv", second.getDiscardFileName());
    assertNull(second.getErrorDescription());

    UploadDetails completed = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)
      .fileName(second.getFileName()).uploadDetailId(124L);
    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED).numTotalFiles(2),
      List.of(finalDetail, completed));

    assertEquals(expectedFileStatus, file.getStatus());
    assertEquals(expectedDetailStatus, first.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.COMPLETED, second.getStatus());
    assertEquals(124L, second.getUploadDetailId());
    assertEquals(10L, second.getNumCorrectlyImportedRows());
    verify(fileRepositoryMock, times(2)).findById(FILE_ID);
    verify(detailRepositoryMock, times(2)).findAllByMigrationFileExecutionId(FILE_ID);
    verify(detailRepositoryMock, times(2)).saveAll(List.of(first, second));
    verify(fileRepositoryMock, times(2)).save(file);
  }

  static Stream<Arguments> incrementalOutcomes() {
    return Stream.of(
      Arguments.of(IngestionFlowFileStatus.COMPLETED, 10L, MigrationFileExecutionDetailStatus.COMPLETED, COMPLETED),
      Arguments.of(IngestionFlowFileStatus.WARNING, 8L, MigrationFileExecutionDetailStatus.PARTIAL, WARNING),
      Arguments.of(IngestionFlowFileStatus.ERROR, 2L, MigrationFileExecutionDetailStatus.FAILED, FAILED)
    );
  }

  @ParameterizedTest
  @EnumSource(value = IngestionFlowFileStatus.class, names = {"UPLOADED", "PROCESSING", "WAITING_FILE"})
  void givenUnknownNonFinalDetailWhenRecordTransferOutcomeThenRejectsWithoutMutation(
    IngestionFlowFileStatus status
  ) {
    MigrationFileExecutionDetail original = exportedDetail();
    prepare(List.of(original));
    UploadDetails pending = detail(status, 10L, 3L)
      .fileName("R_LOMBAR-DEBT_POSITIONS-20261119130346-part001-migration-2_0.csv");
    Uploads upload = upload(UploadsStatusEnum.PROCESSING);
    List<UploadDetails> details = List.of(pending);

    assertThrows(InvalidValueException.class, () -> service.recordTransferOutcome(FILE_ID, upload, details));

    assertEquals(WAITING_PU, file.getStatus());
    assertEquals(UploadsStatusEnum.UPLOADED, file.getUploadStatus());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, original.getStatus());
    assertEquals(FILE_NAME, original.getFileName());
    assertNull(original.getUploadDetailId());
    assertNull(original.getNumTotalRows());
    assertNull(original.getNumCorrectlyImportedRows());
    assertNull(original.getDiscardFileName());
    verifyReads();
  }

  @Test
  void givenCompletedUploadWithUnknownPendingDetailWhenRecordTransferOutcomeThenRejectsEntireSnapshot() {
    MigrationFileExecutionDetail first = exportedDetail();
    MigrationFileExecutionDetail second = exportedDetail("IPA_TEST_3-organizations.csv");
    prepare(List.of(first, second));
    UploadDetails completed = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    UploadDetails pending = detail(IngestionFlowFileStatus.PROCESSING, 10L, 3L)
      .uploadDetailId(124L).fileName("R_LOMBAR-DEBT_POSITIONS-20261119130346-part001-migration-2_0.csv");

    Uploads upload = upload(UploadsStatusEnum.COMPLETED).numTotalFiles(2);
    List<UploadDetails> details = List.of(completed, pending);

    assertThrows(InvalidValueException.class, () -> service.recordTransferOutcome(FILE_ID, upload, details));

    assertEquals(WAITING_PU, file.getStatus());
    assertEquals(UploadsStatusEnum.UPLOADED, file.getUploadStatus());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, first.getStatus());
    assertNull(first.getUploadDetailId());
    assertNull(first.getNumCorrectlyImportedRows());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, second.getStatus());
    assertNull(second.getUploadDetailId());
    assertNull(second.getNumTotalRows());
    verifyReads();
  }

  @ParameterizedTest
  @EnumSource(value = IngestionFlowFileStatus.class, names = {"COMPLETED", "WARNING", "ERROR"})
  void givenUnknownFinalDetailDuringProcessingWhenRecordTransferOutcomeThenRejectsWithoutMutation(
    IngestionFlowFileStatus status
  ) {
    MigrationFileExecutionDetail original = exportedDetail();
    prepare(List.of(original));
    Uploads upload = upload(UploadsStatusEnum.PROCESSING);
    List<UploadDetails> details = List.of(detail(status, 10L, 3L).fileName("IPA_TEST_3-unknown.csv"));

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> service.recordTransferOutcome(FILE_ID, upload, details));

    assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    assertEquals(WAITING_PU, file.getStatus());
    assertEquals(UploadsStatusEnum.UPLOADED, file.getUploadStatus());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, original.getStatus());
    assertNull(original.getUploadDetailId());
    verifyReads();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void givenNoNewDetailsWhenRecordTransferOutcomeThenPreservesProgressiveTransferOutcome(List<UploadDetails> snapshot) {
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.PROCESSING, 10L, 3L);
    original.setErrorDescription("Already reported");
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.PROCESSING), snapshot);

    assertEquals(WAITING_PU, file.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, original.getStatus());
    assertEquals(123L, original.getUploadDetailId());
    assertEquals(10L, original.getNumTotalRows());
    assertEquals(3L, original.getNumCorrectlyImportedRows());
    assertEquals("Already reported", original.getErrorDescription());
    assertEquals("organizations.errors.csv", original.getDiscardFileName());
    verifyReads();
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenSparseTerminalDetailWhenRecordTransferOutcomeThenReplacesPreviousValuesWithCurrentSnapshot() {
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.PROCESSING, 10L, 3L);
    original.setErrorDescription("Previously reported");
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED),
      List.of(detail(IngestionFlowFileStatus.WARNING, null, 8L).discardFileName(null)));

    assertEquals(WARNING, file.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.PARTIAL, original.getStatus());
    assertNull(original.getNumTotalRows());
    assertEquals(8L, original.getNumCorrectlyImportedRows());
    assertNull(original.getErrorDescription());
    assertNull(original.getDiscardFileName());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(original));
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenOnlyBaseNameWhenRecordTransferOutcomeThenRejectsWithoutBindingEitherRow() {
    MigrationFileExecutionDetail first = exportedDetail("first/" + FILE_NAME);
    MigrationFileExecutionDetail second = exportedDetail("second/" + FILE_NAME);
    prepare(List.of(first, second));
    Uploads upload = upload(UploadsStatusEnum.PROCESSING).numTotalFiles(2);
    List<UploadDetails> snapshot = List.of(detail(IngestionFlowFileStatus.PROCESSING, 10L, 3L));

    assertThrows(InvalidValueException.class, () -> service.recordTransferOutcome(FILE_ID, upload, snapshot));

    assertNull(first.getUploadDetailId());
    assertNull(second.getUploadDetailId());
    verifyReads();
  }

  @Test
  void givenIdenticalBaseNamesAndSizesWhenRecordTransferOutcomeThenMatchesFullFileNames() {
    MigrationFileExecutionDetail first = exportedDetail("first/" + FILE_NAME);
    MigrationFileExecutionDetail second = exportedDetail("second/" + FILE_NAME);
    prepare(List.of(first, second));
    when(fileRepositoryMock.save(file)).thenReturn(file);
    UploadDetails firstResult = detail(IngestionFlowFileStatus.COMPLETED, 10L, 10L)
      .fileName(first.getFileName());
    UploadDetails secondResult = detail(IngestionFlowFileStatus.WARNING, 5L, 3L)
      .uploadDetailId(124L).fileName(second.getFileName());

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED).numTotalFiles(2),
      List.of(secondResult, firstResult));

    assertEquals(WARNING, file.getStatus());
    assertEquals(123L, first.getUploadDetailId());
    assertEquals(124L, second.getUploadDetailId());
    assertEquals("first/" + FILE_NAME, first.getFileName());
    assertEquals("second/" + FILE_NAME, second.getFileName());
    assertEquals(10L, first.getNumCorrectlyImportedRows());
    assertEquals(3L, second.getNumCorrectlyImportedRows());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(first, second));
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenKnownUploadIdWithDifferentFileNameWhenRecordTransferOutcomeThenRejectsInsteadOfMatchingById() {
    MigrationFileExecutionDetail first = exportedDetail("first/" + FILE_NAME);
    first.setUploadDetailId(123L);
    MigrationFileExecutionDetail second = exportedDetail("second/" + FILE_NAME);
    second.setUploadDetailId(124L);
    prepare(List.of(first, second));
    Uploads upload = upload(UploadsStatusEnum.PROCESSING).numTotalFiles(2);
    List<UploadDetails> snapshot = List.of(detail(IngestionFlowFileStatus.PROCESSING, 5L, 3L)
      .uploadDetailId(124L));

    assertThrows(InvalidValueException.class, () -> service.recordTransferOutcome(FILE_ID, upload, snapshot));

    assertEquals(WAITING_PU, file.getStatus());
    assertNull(first.getNumCorrectlyImportedRows());
    assertNull(second.getNumCorrectlyImportedRows());
    verifyReads();
  }

  @Test
  void givenOneUnboundDetailWhenRecordTransferOutcomeThenMatchesFullFileNameAndPreservesExistingBinding() {
    MigrationFileExecutionDetail first = exportedDetail("first/" + FILE_NAME);
    first.setUploadDetailId(123L);
    MigrationFileExecutionDetail second = exportedDetail("second/" + FILE_NAME);
    prepare(List.of(first, second));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.PROCESSING).numTotalFiles(2),
      List.of(detail(IngestionFlowFileStatus.PROCESSING, 5L, 3L).uploadDetailId(124L)
        .fileName(second.getFileName())));

    assertEquals(123L, first.getUploadDetailId());
    assertNull(first.getNumTotalRows());
    assertEquals(124L, second.getUploadDetailId());
    assertEquals(3L, second.getNumCorrectlyImportedRows());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(first, second));
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenTerminalReplayWithOmittedFieldsWhenRecordTransferOutcomeThenDoesNotWriteOrClearTransferOutcome() {
    file.setStatus(COMPLETED);
    file.setUploadStatus(UploadsStatusEnum.COMPLETED);
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    prepare(List.of(original));

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.COMPLETED),
      List.of(detail(IngestionFlowFileStatus.COMPLETED, null, null).discardFileName(null)));

    assertEquals(COMPLETED, file.getStatus());
    assertEquals(10L, original.getNumTotalRows());
    assertEquals("organizations.errors.csv", original.getDiscardFileName());
    verifyReads();
  }

  @Test
  void givenRepeatedPuErrorWithoutDetailsWhenRecordTransferOutcomeThenPreservesTerminalFailureWithoutWriting() {
    file.setStatus(FAILED);
    file.setUploadStatus(UploadsStatusEnum.ERROR);
    MigrationFileExecutionDetail original = exportedDetail();
    original.setStatus(MigrationFileExecutionDetailStatus.FAILED);
    prepare(List.of(original));

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.ERROR), List.of());

    assertEquals(FAILED, file.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.FAILED, original.getStatus());
    verifyReads();
  }

  @ParameterizedTest
  @EnumSource(value = IngestionFlowFileStatus.class, names = {"UPLOADED", "PROCESSING", "WAITING_FILE"})
  void givenFailureWithUnfinishedDetailWhenRecordTransferOutcomeThenReplaysWithoutWriting(
    IngestionFlowFileStatus pendingStatus
  ) {
    MigrationFileExecutionDetail first = exportedDetail();
    MigrationFileExecutionDetail second = exportedDetail("IPA_TEST_3-organizations.csv");
    prepare(List.of(first, second));
    when(fileRepositoryMock.save(file)).thenReturn(file);
    Uploads upload = upload(UploadsStatusEnum.COMPLETED).numTotalFiles(2);
    List<UploadDetails> snapshot = List.of(detail(IngestionFlowFileStatus.ERROR, 10L, 2L),
      detail(pendingStatus, 5L, 1L).uploadDetailId(124L).fileName(second.getFileName()));

    service.recordTransferOutcome(FILE_ID, upload, snapshot);
    service.recordTransferOutcome(FILE_ID, upload, snapshot);

    assertEquals(FAILED, file.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.FAILED, first.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.FAILED, second.getStatus());
    assertEquals(124L, second.getUploadDetailId());
    assertEquals(5L, second.getNumTotalRows());
    assertEquals(1L, second.getNumCorrectlyImportedRows());
    verify(fileRepositoryMock, times(2)).findById(FILE_ID);
    verify(detailRepositoryMock, times(2)).findAllByMigrationFileExecutionId(FILE_ID);
    verify(detailRepositoryMock).saveAll(List.of(first, second));
    verify(fileRepositoryMock).save(file);
  }

  @Test
  void givenFailedReplayWithChangedCountsWhenRecordTransferOutcomeThenKeepsTheStoredCounts() {
    file.setStatus(FAILED);
    file.setUploadStatus(UploadsStatusEnum.ERROR);
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.ERROR, 10L, 3L);
    prepare(List.of(original));
    Uploads upload = upload(UploadsStatusEnum.ERROR);
    List<UploadDetails> snapshot = List.of(detail(IngestionFlowFileStatus.PROCESSING, 10L, 4L));

    assertSame(file, service.recordTransferOutcome(FILE_ID, upload, snapshot));

    assertEquals(3L, original.getNumCorrectlyImportedRows());
    assertEquals(MigrationFileExecutionDetailStatus.FAILED, original.getStatus());
    verifyReads();
  }

  @Test
  void givenPreviouslyFinalizedDetailWhenRecordTransferOutcomeThenMapsCurrentSnapshotIndependentlyOfUploadStatus() {
    MigrationFileExecutionDetail original = localDetail(IngestionFlowFileStatus.COMPLETED, 10L, 10L);
    prepare(List.of(original));
    when(fileRepositoryMock.save(file)).thenReturn(file);

    service.recordTransferOutcome(FILE_ID, upload(UploadsStatusEnum.PROCESSING),
      List.of(detail(IngestionFlowFileStatus.COMPLETED, null, null).discardFileName(null)));

    assertEquals(WAITING_PU, file.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.COMPLETED, original.getStatus());
    assertNull(original.getNumTotalRows());
    assertNull(original.getNumCorrectlyImportedRows());
    assertNull(original.getDiscardFileName());
    verifyReads();
    verify(detailRepositoryMock).saveAll(List.of(original));
    verify(fileRepositoryMock).save(file);
  }

  private void prepare(List<MigrationFileExecutionDetail> existing) {
    when(fileRepositoryMock.findById(FILE_ID)).thenReturn(Optional.of(file));
    when(detailRepositoryMock.findAllByMigrationFileExecutionId(FILE_ID)).thenReturn(existing);
  }

  private void verifyReads() {
    verify(fileRepositoryMock).findById(FILE_ID);
    verify(detailRepositoryMock).findAllByMigrationFileExecutionId(FILE_ID);
  }

  private MigrationFileExecutionDetail localDetail(IngestionFlowFileStatus status, Long total, Long accepted) {
    MigrationFileExecutionDetail original = exportedDetail();
    MigrationFileExecutionDetailMapper mapper = new MigrationFileExecutionDetailMapper();
    mapper.updateMigrationFileExecutionDetail(original, detail(status, total, accepted));
    return original;
  }

  private MigrationFileExecutionDetail exportedDetail() {
    MigrationFileExecutionDetail detail = exportedDetail(FILE_NAME);
    detail.setId(DETAIL_ID);
    return detail;
  }

  private MigrationFileExecutionDetail exportedDetail(String fileName) {
    MigrationFileExecutionDetail detail = new MigrationFileExecutionDetailMapper()
      .toMigrationFileExecutionDetail(FILE_ID, fileName, 500L);
    detail.setId(UUID.randomUUID());
    detail.setCreationDate(ACCEPTED_AT.minusHours(1));
    detail.setStatus(MigrationFileExecutionDetailStatus.WAITING_PU);
    return detail;
  }

  private static Uploads upload(UploadsStatusEnum status) {
    return new Uploads().uploadId(42L).status(status).numTotalFiles(1);
  }

  private static UploadDetails detail(IngestionFlowFileStatus status, Long total, Long accepted) {
    return new UploadDetails().uploadId(42L).uploadDetailId(123L).fileName(FILE_NAME)
      .fileSize(500L).status(status).numTotalRows(total).numCorrectlyImportedRows(accepted)
      .discardFileName("organizations.errors.csv");
  }
}
