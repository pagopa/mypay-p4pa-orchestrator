package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.EvidenceException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
  "spring.datasource.url=jdbc:h2:mem:evidence-persistence-test;DB_CLOSE_DELAY=-1",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MigrationEvidencePersistenceTest {

  @Autowired private MigrationFileExecutionEvidenceService service;
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
  void givenConcurrentIdenticalSnapshotsWhenRecordEvidenceThenEnrichesOneDetailAndPreservesTerminalState() throws Exception {
    createFile();
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> recordAfter(start));
      var second = executor.submit(() -> recordAfter(start));
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }

    assertEquals(MigrationFileExecutionStatus.COMPLETED, fileRepository.findById(fileId).orElseThrow().getStatus());
    var details = detailRepository.findAllByMigrationFileExecutionId(fileId);
    assertEquals(1, details.size());
    assertEquals(detailId, details.getFirst().getId());
    assertEquals("IPA_TEST_2", details.getFirst().getIpaCode());
    assertEquals(91L, details.getFirst().getUploadDetailId());
    assertEquals(1, details.getFirst().getNumCorrectlyImportedRows());
  }

  @Test
  void givenTransactionFailureWhenRecordEvidenceThenRollsBackBothFileAndDetails() {
    createFile();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThrows(EvidenceException.class, () -> transaction.executeWithoutResult(status -> {
      recordEvidence();
      throw new EvidenceException("EVIDENCE_TEST_FAILURE", "Force rollback after both writes");
    }));

    MigrationFileExecution persisted = fileRepository.findById(fileId).orElseThrow();
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, persisted.getStatus());
    assertEquals(UploadsStatusEnum.UPLOADED, persisted.getUploadStatus());
    var details = detailRepository.findAllByMigrationFileExecutionId(fileId);
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
  void givenIdenticalSnapshotWhenRecordEvidenceThenPreservesDetailAuditTimestamp(UploadsStatusEnum status) {
    createFile();
    Uploads upload = new Uploads().uploadId(42L).status(status).numTotalFiles(1);
    List<UploadDetails> snapshot = List.of(new UploadDetails().uploadId(42L).uploadDetailId(91L)
      .fileName("IPA_TEST_2-organizations.csv").fileSize(1L)
      .status(status == UploadsStatusEnum.COMPLETED
        ? IngestionFlowFileStatus.COMPLETED : IngestionFlowFileStatus.PROCESSING)
      .numTotalRows(1L).numCorrectlyImportedRows(1L));
    service.recordEvidence(fileId, 42L, upload, snapshot);
    MigrationFileExecutionDetail before = detailRepository.findById(detailId).orElseThrow();

    service.recordEvidence(fileId, 42L, upload, snapshot);

    MigrationFileExecutionDetail after = detailRepository.findById(detailId).orElseThrow();
    assertNotNull(before.getUpdateDate());
    assertEquals(before.getUpdateDate(), after.getUpdateDate());
    assertEquals(before.getCreationDate(), after.getCreationDate());
    assertEquals(detailId, after.getId());
    assertEquals("IPA_TEST_2", after.getIpaCode());
    assertEquals(status == UploadsStatusEnum.COMPLETED ? 91L : null, after.getUploadDetailId());
    assertEquals(before.getStatus(), after.getStatus());
  }

  @ParameterizedTest
  @EnumSource(value = IngestionFlowFileStatus.class, names = {"COMPLETED", "WARNING", "ERROR"})
  void givenFinalDetailDuringProcessingWhenRecordEvidenceThenPersistsDetailBeforeUploadCompletes(
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

    service.recordEvidence(fileId, 42L,
      new Uploads().uploadId(42L).status(UploadsStatusEnum.PROCESSING).numTotalFiles(1), snapshot);

    MigrationFileExecution pending = fileRepository.findById(fileId).orElseThrow();
    MigrationFileExecutionDetail persisted = detailRepository.findById(detailId).orElseThrow();
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, pending.getStatus());
    assertEquals(UploadsStatusEnum.PROCESSING, pending.getUploadStatus());
    assertNull(pending.getUploadErrorCode());
    assertEquals(expectedDetailStatus, persisted.getStatus());
    assertEquals(91L, persisted.getUploadDetailId());
    assertEquals(2, persisted.getNumTotalRows());
    assertEquals((int) accepted, persisted.getNumCorrectlyImportedRows());

    service.recordEvidence(fileId, 42L,
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
    assertEquals(persisted.getUpdateDate(), completed.getUpdateDate());
    assertEquals(persisted.getCreationDate(), completed.getCreationDate());
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

  private void recordAfter(CountDownLatch start) {
    try {
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Concurrent writer did not start");
      }
      recordEvidence();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private void recordEvidence() {
    service.recordEvidence(fileId, 42L,
      new Uploads().uploadId(42L).status(UploadsStatusEnum.COMPLETED).numTotalFiles(1),
      List.of(new UploadDetails().uploadId(42L).uploadDetailId(91L).fileName("IPA_TEST_2-organizations.csv")
        .fileSize(1L).status(IngestionFlowFileStatus.COMPLETED).numTotalRows(1L).numCorrectlyImportedRows(1L)));
  }
}
