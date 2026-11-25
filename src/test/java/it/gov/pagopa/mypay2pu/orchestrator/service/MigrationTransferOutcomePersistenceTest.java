package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.url=jdbc:h2:mem:transfer-outcome-persistence-test;DB_CLOSE_DELAY=-1",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MigrationTransferOutcomePersistenceTest {

  @Autowired private MigrationFileExecutionTransferOutcomeService service;
  @Autowired private MigrationFileExecutionRepository fileRepository;
  @Autowired private MigrationFileExecutionDetailRepository detailRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  private UUID fileId;
  private UUID detailId;

  @AfterEach
  void cleanUp() {
    if (fileId != null) {
      detailRepository.deleteAll(detailRepository.findAllByMigrationFileExecutionId(fileId));
      fileRepository.deleteById(fileId);
    }
  }

  @Test
  void givenIdenticalSnapshotsWhenRecordTransferOutcomeThenEnrichesOneDetailAndPreservesTerminalState() {
    createFile();
    recordTransferOutcome();
    MigrationFileExecutionDetail firstOutcome = detailRepository.findById(detailId).orElseThrow();

    recordTransferOutcome();

    assertEquals(MigrationFileExecutionStatus.COMPLETED, fileRepository.findById(fileId).orElseThrow().getStatus());
    List<MigrationFileExecutionDetail> details = detailRepository.findAllByMigrationFileExecutionId(fileId);
    assertEquals(1, details.size());
    assertEquals(detailId, details.getFirst().getId());
    assertEquals("IPA_TEST_2", details.getFirst().getIpaCode());
    assertEquals(91L, details.getFirst().getUploadDetailId());
    assertEquals(1L, details.getFirst().getNumCorrectlyImportedRows());
    assertEquals(firstOutcome, details.getFirst());
    assertEquals(firstOutcome.getUpdateDate(), details.getFirst().getUpdateDate());
  }

  @Test
  void givenLongCountsWhenRecordTransferOutcomeThenPersistsWithoutNarrowing() {
    createFile();
    Uploads upload = new Uploads().uploadId(42L).status(UploadsStatusEnum.COMPLETED).numTotalFiles(1);
    UploadDetails detail = new UploadDetails().uploadId(42L).uploadDetailId(91L)
      .fileName("IPA_TEST_2-organizations.csv").fileSize(1L).status(IngestionFlowFileStatus.WARNING)
      .numTotalRows(Long.MAX_VALUE).numCorrectlyImportedRows(Long.MAX_VALUE - 1);

    service.recordTransferOutcome(fileId, upload, List.of(detail));

    MigrationFileExecutionDetail persisted = detailRepository.findById(detailId).orElseThrow();
    assertEquals(Long.MAX_VALUE, persisted.getNumTotalRows());
    assertEquals(Long.MAX_VALUE - 1, persisted.getNumCorrectlyImportedRows());
    assertEquals(MigrationFileExecutionStatus.WARNING, fileRepository.findById(fileId).orElseThrow().getStatus());
  }

  @Test
  void givenTransactionFailureWhenRecordTransferOutcomeThenRollsBackBothFileAndDetails() {
    createFile();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
      recordTransferOutcome();
      throw new IllegalStateException("Force rollback after both writes");
    }));

    MigrationFileExecution persisted = fileRepository.findById(fileId).orElseThrow();
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, persisted.getStatus());
    assertEquals(UploadsStatusEnum.UPLOADED, persisted.getUploadStatus());
    List<MigrationFileExecutionDetail> details = detailRepository.findAllByMigrationFileExecutionId(fileId);
    assertEquals(1, details.size());
    assertEquals(detailId, details.getFirst().getId());
    assertEquals("IPA_TEST_2", details.getFirst().getIpaCode());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, details.getFirst().getStatus());
    assertNull(details.getFirst().getUploadDetailId());
    assertNull(details.getFirst().getNumTotalRows());
  }

  @Test
  void givenExistingFileWhenGetRequiredThenUsesSharedLookupThroughTransactionalProxy() {
    createFile();
    MigrationFileExecution persisted = fileRepository.findById(fileId).orElseThrow();

    MigrationFileExecution result = service.getRequired(persisted.getMigrationId(), MigrationFileType.ORGANIZATIONS);

    assertEquals(fileId, result.getId());
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, result.getStatus());
  }

  @ParameterizedTest
  @EnumSource(value = UploadsStatusEnum.class, names = {"PROCESSING", "COMPLETED"})
  void givenIdenticalSnapshotWhenRecordTransferOutcomeThenPreservesDetailAuditTimestamp(UploadsStatusEnum status) {
    createFile();
    Uploads upload = new Uploads().uploadId(42L).status(status).numTotalFiles(1);
    List<UploadDetails> snapshot = List.of(new UploadDetails().uploadId(42L).uploadDetailId(91L)
      .fileName("IPA_TEST_2-organizations.csv").fileSize(1L)
      .status(status == UploadsStatusEnum.COMPLETED
        ? IngestionFlowFileStatus.COMPLETED : IngestionFlowFileStatus.PROCESSING)
      .numTotalRows(1L).numCorrectlyImportedRows(1L));
    service.recordTransferOutcome(fileId, upload, snapshot);
    MigrationFileExecutionDetail before = detailRepository.findById(detailId).orElseThrow();

    service.recordTransferOutcome(fileId, upload, snapshot);

    MigrationFileExecutionDetail after = detailRepository.findById(detailId).orElseThrow();
    assertNotNull(before.getUpdateDate());
    assertEquals(before.getUpdateDate(), after.getUpdateDate());
    assertEquals(before.getCreationDate(), after.getCreationDate());
    assertEquals(detailId, after.getId());
    assertEquals("IPA_TEST_2", after.getIpaCode());
    assertEquals(91L, after.getUploadDetailId());
    assertEquals(before.getStatus(), after.getStatus());
  }

  @ParameterizedTest
  @EnumSource(value = IngestionFlowFileStatus.class, names = {"COMPLETED", "WARNING", "ERROR"})
  void givenFinalDetailDuringProcessingWhenRecordTransferOutcomeThenPersistsDetailBeforeUploadCompletes(
    IngestionFlowFileStatus status
  ) {
    createFile();
    long accepted = status == IngestionFlowFileStatus.COMPLETED ? 2L : 1L;
    List<UploadDetails> snapshot = List.of(new UploadDetails().uploadId(42L).uploadDetailId(91L)
      .fileName("IPA_TEST_2-organizations.csv").fileSize(1L).status(status)
      .numTotalRows(2L).numCorrectlyImportedRows(accepted));
    MigrationFileExecutionDetailStatus expectedDetailStatus = switch (status) {
      case COMPLETED -> MigrationFileExecutionDetailStatus.COMPLETED;
      case WARNING -> MigrationFileExecutionDetailStatus.PARTIAL;
      case ERROR -> MigrationFileExecutionDetailStatus.FAILED;
      default -> throw new AssertionError("Unexpected test status: " + status);
    };

    service.recordTransferOutcome(fileId,
      new Uploads().uploadId(42L).status(UploadsStatusEnum.PROCESSING).numTotalFiles(1), snapshot);

    MigrationFileExecution pending = fileRepository.findById(fileId).orElseThrow();
    MigrationFileExecutionDetail persisted = detailRepository.findById(detailId).orElseThrow();
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, pending.getStatus());
    assertEquals(UploadsStatusEnum.PROCESSING, pending.getUploadStatus());
    assertNull(pending.getUploadErrorCode());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, persisted.getStatus());
    assertEquals(91L, persisted.getUploadDetailId());
    assertEquals(2L, persisted.getNumTotalRows());
    assertEquals(accepted, persisted.getNumCorrectlyImportedRows());

    service.recordTransferOutcome(fileId,
      new Uploads().uploadId(42L).status(UploadsStatusEnum.COMPLETED).numTotalFiles(1), snapshot);

    MigrationFileExecutionStatus expectedFileStatus = switch (status) {
      case COMPLETED -> MigrationFileExecutionStatus.COMPLETED;
      case WARNING -> MigrationFileExecutionStatus.WARNING;
      case ERROR -> MigrationFileExecutionStatus.FAILED;
      default -> throw new AssertionError("Unexpected test status: " + status);
    };
    assertEquals(expectedFileStatus, fileRepository.findById(fileId).orElseThrow().getStatus());
    MigrationFileExecutionDetail completed = detailRepository.findById(detailId).orElseThrow();
    assertEquals(expectedDetailStatus, completed.getStatus());
    assertFalse(completed.getUpdateDate().isBefore(persisted.getUpdateDate()));
    assertEquals(persisted.getCreationDate(), completed.getCreationDate());
  }

  @Test
  void givenSuccessivePartialSnapshotsWhenRecordTransferOutcomeThenAccumulatesWithoutPrematureFinalization() {
    createFile();
    UUID secondId = detailRepository.saveAndFlush(MigrationFileExecutionDetail.builder()
      .migrationFileExecutionId(fileId).fileName("IPA_TEST_3-organizations.csv").ipaCode("IPA_TEST_3")
      .fileSize(2L).status(MigrationFileExecutionDetailStatus.WAITING_PU).build()).getId();
    Uploads processing = new Uploads().uploadId(42L).status(UploadsStatusEnum.PROCESSING).numTotalFiles(2);
    UploadDetails first = new UploadDetails().uploadId(42L).uploadDetailId(91L)
      .fileName("IPA_TEST_2-organizations.csv").fileSize(1L).status(IngestionFlowFileStatus.PROCESSING)
      .numTotalRows(10L).numCorrectlyImportedRows(4L).errorDescription("Two rejected rows")
      .discardFileName("rejects.csv");
    UploadDetails second = new UploadDetails().uploadId(42L).uploadDetailId(92L)
      .fileName("IPA_TEST_3-organizations.csv").fileSize(2L).status(IngestionFlowFileStatus.PROCESSING)
      .numTotalRows(5L).numCorrectlyImportedRows(3L);

    service.recordTransferOutcome(fileId, processing, List.of(first));
    MigrationFileExecutionDetail firstObservation = detailRepository.findById(detailId).orElseThrow();
    service.recordTransferOutcome(fileId, processing, List.of(second));
    service.recordTransferOutcome(fileId, processing, null);
    service.recordTransferOutcome(fileId, processing, List.of());

    assertEquals(MigrationFileExecutionStatus.WAITING_PU, fileRepository.findById(fileId).orElseThrow().getStatus());
    MigrationFileExecutionDetail preserved = detailRepository.findById(detailId).orElseThrow();
    assertEquals(firstObservation, preserved);
    assertEquals(firstObservation.getUpdateDate(), preserved.getUpdateDate());
    assertEquals(2, detailRepository.findAllByMigrationFileExecutionId(fileId).size());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, preserved.getStatus());
    assertEquals(4L, preserved.getNumCorrectlyImportedRows());
    assertEquals(3L, detailRepository.findById(secondId).orElseThrow().getNumCorrectlyImportedRows());

    UploadDetails firstTerminal = new UploadDetails().uploadId(42L).uploadDetailId(91L)
      .fileName(first.getFileName()).fileSize(1L).status(IngestionFlowFileStatus.WARNING)
      .numCorrectlyImportedRows(8L);
    UploadDetails secondTerminal = new UploadDetails().uploadId(42L).uploadDetailId(92L)
      .fileName(second.getFileName()).fileSize(2L).status(IngestionFlowFileStatus.COMPLETED)
      .numCorrectlyImportedRows(5L);
    Uploads completed = new Uploads().uploadId(42L).status(UploadsStatusEnum.COMPLETED)
      .numTotalFiles(2).numCorrectlyProcessedFiles(2);

    service.recordTransferOutcome(fileId, completed, List.of(firstTerminal));
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, fileRepository.findById(fileId).orElseThrow().getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU,
      detailRepository.findById(detailId).orElseThrow().getStatus());
    service.recordTransferOutcome(fileId, completed, List.of(secondTerminal, firstTerminal));

    MigrationFileExecutionDetail finalized = detailRepository.findById(detailId).orElseThrow();
    MigrationFileExecutionDetail secondFinalized = detailRepository.findById(secondId).orElseThrow();
    assertEquals(MigrationFileExecutionStatus.WARNING, fileRepository.findById(fileId).orElseThrow().getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.PARTIAL, finalized.getStatus());
    assertEquals(MigrationFileExecutionDetailStatus.COMPLETED, secondFinalized.getStatus());
    assertEquals(10L, finalized.getNumTotalRows());
    assertEquals(8L, finalized.getNumCorrectlyImportedRows());
    assertEquals(5L, secondFinalized.getNumTotalRows());
    assertEquals(5L, secondFinalized.getNumCorrectlyImportedRows());
    assertEquals("Two rejected rows", finalized.getErrorDescription());
    assertEquals("rejects.csv", finalized.getDiscardFileName());

    service.recordTransferOutcome(fileId, completed, List.of(firstTerminal, secondTerminal));

    assertEquals(finalized, detailRepository.findById(detailId).orElseThrow());
    assertEquals(secondFinalized, detailRepository.findById(secondId).orElseThrow());
    assertEquals(finalized.getUpdateDate(), detailRepository.findById(detailId).orElseThrow().getUpdateDate());
    assertEquals(secondFinalized.getUpdateDate(), detailRepository.findById(secondId).orElseThrow().getUpdateDate());
    assertEquals(2, detailRepository.findAllByMigrationFileExecutionId(fileId).size());
  }

  @Test
  void givenProgressiveTransferOutcomeWhenMarkFailedThenPreservesCollectedValuesAndPuStatus() {
    createFile();
    service.recordTransferOutcome(fileId, new Uploads().uploadId(42L).status(UploadsStatusEnum.PROCESSING),
      List.of(new UploadDetails().uploadId(42L).uploadDetailId(91L)
        .fileName("IPA_TEST_2-organizations.csv").fileSize(1L).status(IngestionFlowFileStatus.PROCESSING)
        .numTotalRows(10L).numCorrectlyImportedRows(3L).discardFileName("rejects.csv")));

    service.markFailed(fileId, "TRANSFER_OUTCOME_TIMEOUT", "Timed out");

    MigrationFileExecution file = fileRepository.findById(fileId).orElseThrow();
    MigrationFileExecutionDetail detail = detailRepository.findById(detailId).orElseThrow();
    assertEquals(MigrationFileExecutionStatus.FAILED, file.getStatus());
    assertEquals(UploadsStatusEnum.PROCESSING, file.getUploadStatus());
    assertEquals("TRANSFER_OUTCOME_TIMEOUT", file.getUploadErrorCode());
    assertEquals(MigrationFileExecutionDetailStatus.FAILED, detail.getStatus());
    assertEquals(91L, detail.getUploadDetailId());
    assertEquals(10L, detail.getNumTotalRows());
    assertEquals(3L, detail.getNumCorrectlyImportedRows());
    assertEquals("rejects.csv", detail.getDiscardFileName());
    assertEquals(1, detailRepository.findAllByMigrationFileExecutionId(fileId).size());
  }

  private void createFile() {
    fileId = fileRepository.saveAndFlush(MigrationFileExecution.builder().migrationId(UUID.randomUUID())
      .fileType(MigrationFileType.ORGANIZATIONS).status(MigrationFileExecutionStatus.WAITING_PU)
      .uploadId(42L).uploadStatus(UploadsStatusEnum.UPLOADED)
      .uploadCompletedAt(LocalDateTime.of(2026, Month.NOVEMBER, 18, 12, 0)).build()).getId();
    detailId = detailRepository.saveAndFlush(MigrationFileExecutionDetail.builder()
      .migrationFileExecutionId(fileId).fileName("IPA_TEST_2-organizations.csv").ipaCode("IPA_TEST_2")
      .fileSize(1L).status(MigrationFileExecutionDetailStatus.WAITING_PU).build()).getId();
  }

  private void recordTransferOutcome() {
    service.recordTransferOutcome(fileId,
      new Uploads().uploadId(42L).status(UploadsStatusEnum.COMPLETED).numTotalFiles(1),
      List.of(new UploadDetails().uploadId(42L).uploadDetailId(91L).fileName("IPA_TEST_2-organizations.csv")
        .fileSize(1L).status(IngestionFlowFileStatus.COMPLETED).numTotalRows(1L).numCorrectlyImportedRows(1L)));
  }
}
