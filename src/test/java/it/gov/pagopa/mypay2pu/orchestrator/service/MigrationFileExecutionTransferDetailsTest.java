package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.url=jdbc:h2:mem:detail-lifecycle-test;DB_CLOSE_DELAY=-1",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MigrationFileExecutionTransferDetailsTest {

  private static final MigrationFileType FILE_TYPE = MigrationFileType.ORGANIZATIONS;
  private static final String FILE_NAME = "IPA_TEST_2-organizations.csv";
  private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 17, 16, 0);

  @TempDir
  private static Path sharedFolder;

  @DynamicPropertySource
  static void storageProperties(DynamicPropertyRegistry registry) {
    registry.add("orchestrator.storage.extractor-output-path", () -> sharedFolder.toString());
  }

  @Autowired
  private MigrationFileExecutionRepository executionRepository;
  @Autowired
  private MigrationFileExecutionDetailRepository detailRepository;
  @Autowired
  private MigrationFileExecutionExportService exportService;
  @Autowired
  private MigrationFileExecutionTransferService transferService;
  @Autowired
  private MigrationFileExecutionTransferOutcomeService transferOutcomeService;

  @Test
  void givenExtractedFileWhenRecordUploadAcceptedThenUpdatesOnlyExistingDetailStatus() throws IOException {
    MigrationFileExecution execution = completeExtraction();
    MigrationFileExecutionDetail exportedDetail = details(execution).getFirst();
    assertEquals(MigrationFileExecutionDetailStatus.EXPORTED, exportedDetail.getStatus());
    assertEquals(FILE_NAME, exportedDetail.getFileName());
    assertEquals("IPA_TEST_2", exportedDetail.getIpaCode());
    assertEquals(0L, exportedDetail.getFileSize());
    assertNull(exportedDetail.getUploadDetailId());
    assertNull(exportedDetail.getNumTotalRows());

    transferService.markTransferring(execution.getMigrationId(), FILE_TYPE, COMPLETED_AT);
    assertEquals(MigrationFileExecutionDetailStatus.TRANSFERING, details(execution).getFirst().getStatus());
    assertEquals("IPA_TEST_2", details(execution).getFirst().getIpaCode());

    transferService.recordUploadAccepted(
      execution.getMigrationId(), FILE_TYPE, 42L, UploadsStatusEnum.UPLOADED, COMPLETED_AT
    );

    List<MigrationFileExecutionDetail> acceptedDetails = details(execution);
    assertEquals(1, acceptedDetails.size());
    MigrationFileExecutionDetail acceptedDetail = acceptedDetails.getFirst();
    assertEquals(exportedDetail.getId(), acceptedDetail.getId());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, acceptedDetail.getStatus());
    assertEquals("IPA_TEST_2", acceptedDetail.getIpaCode());
    exportedDetail.setStatus(MigrationFileExecutionDetailStatus.WAITING_PU);
    assertEquals(exportedDetail, acceptedDetail);
  }

  @Test
  void givenExtractedFileWhenMarkTransferFailedThenUpdatesExistingDetails() throws IOException {
    MigrationFileExecution execution = completeExtraction();
    UUID detailId = details(execution).getFirst().getId();

    transferService.markTransferFailed(execution.getMigrationId(), FILE_TYPE, "UPLOAD_ERROR", "Upload failed");

    assertEquals(detailId, details(execution).getFirst().getId());
    assertEquals(MigrationFileExecutionDetailStatus.FAILED, details(execution).getFirst().getStatus());
    assertEquals("IPA_TEST_2", details(execution).getFirst().getIpaCode());
  }

  @Test
  void givenInvalidAcceptedUploadWhenRecordUploadAcceptedThenKeepsDetailState() throws IOException {
    MigrationFileExecution execution = completeExtraction();
    UUID migrationId = execution.getMigrationId();
    transferService.markTransferring(migrationId, FILE_TYPE, COMPLETED_AT);

    assertThrows(InvalidValueException.class, () -> transferService.recordUploadAccepted(
      migrationId, FILE_TYPE, 0L, UploadsStatusEnum.UPLOADED, COMPLETED_AT
    ));

    assertEquals(MigrationFileExecutionDetailStatus.TRANSFERING, details(execution).getFirst().getStatus());
    assertEquals(MigrationFileExecutionStatus.TRANSFERING,
      executionRepository.findById(execution.getId()).orElseThrow().getStatus());
  }

  @Test
  void givenCompletedExtractionWhenMarkExportedAgainThenDoesNotDuplicateDetails() throws IOException {
    MigrationFileExecution execution = completeExtraction();
    UUID detailId = details(execution).getFirst().getId();

    exportService.markExported(execution.getMigrationId(), FILE_TYPE, archives(), COMPLETED_AT);

    assertEquals(1, details(execution).size());
    assertEquals(detailId, details(execution).getFirst().getId());
    assertEquals("IPA_TEST_2", details(execution).getFirst().getIpaCode());
  }

  @Test
  void givenUnavailableArchiveWhenMarkExportedThenPersistsDeclaredDetailsWithoutReadingArchive() {
    MigrationFileExecution execution = exportingExecution();
    UUID migrationId = execution.getMigrationId();
    List<ArchiveFile> declaredArchives = List.of(
      new ArchiveFile("organizations.zip", List.of(FILE_NAME, "IPA_TEST_2-missing.csv"))
    );

    assertFalse(Files.exists(sharedFolder.resolve(execution.getExtractionId())));

    exportService.markExported(migrationId, FILE_TYPE, declaredArchives, COMPLETED_AT);

    List<MigrationFileExecutionDetail> exportedDetails = details(execution);
    assertEquals(2, exportedDetails.size());
    assertEquals(Set.of(FILE_NAME, "IPA_TEST_2-missing.csv"),
      exportedDetails.stream().map(MigrationFileExecutionDetail::getFileName).collect(Collectors.toSet()));
    exportedDetails.forEach(detail -> {
      assertEquals(0L, detail.getFileSize());
      assertEquals(MigrationFileExecutionDetailStatus.EXPORTED, detail.getStatus());
    });
    MigrationFileExecution persisted = executionRepository.findById(execution.getId()).orElseThrow();
    assertEquals(MigrationFileExecutionStatus.EXPORTED, persisted.getStatus());
    assertEquals(COMPLETED_AT, persisted.getExtractionCompletedAt());
    assertEquals("organizations.zip", persisted.getFilePath());
    assertFalse(Files.exists(sharedFolder.resolve(execution.getExtractionId())));
  }

  @Test
  void givenMultipleExtractedFilesWhenMarkExportedThenCreatesOneDetailPerFile() throws IOException {
    MigrationFileExecution execution = exportingExecution();
    List<String> files = List.of("IPA_TEST_2-part-1.csv", "IPA_TEST_3-part-2.csv");
    createArchive(execution, files);

    exportService.markExported(execution.getMigrationId(), FILE_TYPE, List.of(
      new ArchiveFile("organizations.zip", files)
    ), COMPLETED_AT);

    List<MigrationFileExecutionDetail> exportedDetails = details(execution);
    assertEquals(2, exportedDetails.size());
    assertEquals(Set.of("IPA_TEST_2-part-1.csv", "IPA_TEST_3-part-2.csv"),
      exportedDetails.stream().map(MigrationFileExecutionDetail::getFileName).collect(Collectors.toSet()));
    assertEquals(Set.of("IPA_TEST_2", "IPA_TEST_3"),
      exportedDetails.stream().map(MigrationFileExecutionDetail::getIpaCode).collect(Collectors.toSet()));
    assertTrue(exportedDetails.stream().allMatch(detail -> detail.getFileName().startsWith(detail.getIpaCode() + "-")));
    exportedDetails.forEach(detail -> {
      assertEquals(0L, detail.getFileSize());
      assertEquals(MigrationFileExecutionDetailStatus.EXPORTED, detail.getStatus());
    });
  }

  @Test
  void givenDetailPersistenceFailureWhenMarkExportedThenRollsBackCompletionAndAllDetails() throws IOException {
    MigrationFileExecution execution = exportingExecution();
    UUID migrationId = execution.getMigrationId();
    String longFileName = "IPA_TEST_2-" + "a".repeat(300) + ".csv";
    List<String> files = List.of("IPA_TEST_2-valid.csv", longFileName);
    createArchive(execution, files);
    List<ArchiveFile> oversizedArchives = List.of(
      new ArchiveFile("organizations.zip", files)
    );

    assertThrows(DataIntegrityViolationException.class, () -> exportService.markExported(
      migrationId, FILE_TYPE, oversizedArchives, COMPLETED_AT
    ));

    assertTrue(details(execution).isEmpty());
    assertEquals(MigrationFileExecutionStatus.EXPORTING,
      executionRepository.findById(execution.getId()).orElseThrow().getStatus());
  }

  @Test
  void givenFileWithoutIpaWhenMarkExportedThenRollsBackDetailsAndCompletion() throws IOException {
    MigrationFileExecution execution = exportingExecution();
    UUID migrationId = execution.getMigrationId();
    List<String> files = List.of(FILE_NAME, "organizations.csv");
    createArchive(execution, files);
    List<ArchiveFile> invalidArchives = List.of(new ArchiveFile("organizations.zip", files));

    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> exportService.markExported(migrationId, FILE_TYPE, invalidArchives, COMPLETED_AT));

    assertEquals("EXTRACTOR_INVALID_FILE_DETAILS", failure.getCode());
    assertTrue(details(execution).isEmpty());
    MigrationFileExecution persisted = executionRepository.findById(execution.getId()).orElseThrow();
    assertEquals(MigrationFileExecutionStatus.EXPORTING, persisted.getStatus());
    assertNull(persisted.getExtractionCompletedAt());
    assertNull(persisted.getFilePath());
  }

  @ParameterizedTest
  @MethodSource("puOutcomes")
  void givenExportedFilesWhenRecordTransferOutcomeThenEnrichesOriginalDetailsAndReplaysWithoutDuplicates(
    IngestionFlowFileStatus puStatus, long accepted, MigrationFileExecutionStatus expected
  ) throws IOException {
    MigrationFileExecution execution = exportingExecution();
    List<String> files = List.of(FILE_NAME, "IPA_TEST_3-organizations.csv");
    createArchive(execution, files);
    UUID migrationId = execution.getMigrationId();
    exportService.markExported(migrationId, FILE_TYPE, List.of(new ArchiveFile("organizations.zip", files)), COMPLETED_AT);
    List<MigrationFileExecutionDetail> exported = details(execution);
    transferService.markTransferring(migrationId, FILE_TYPE, COMPLETED_AT);
    transferService.recordUploadAccepted(migrationId, FILE_TYPE, 42L, UploadsStatusEnum.UPLOADED, COMPLETED_AT);
    Uploads upload = new Uploads().uploadId(42L).status(UploadsStatusEnum.COMPLETED).numTotalFiles(2);
    List<UploadDetails> snapshot = List.of(
      new UploadDetails().uploadId(42L).uploadDetailId(124L).fileName(files.getLast()).fileSize(5L)
        .status(puStatus).numTotalRows(10L).numCorrectlyImportedRows(accepted),
      new UploadDetails().uploadId(42L).uploadDetailId(123L).fileName(FILE_NAME).fileSize(5L)
        .status(puStatus).numTotalRows(10L).numCorrectlyImportedRows(accepted)
    );

    transferOutcomeService.recordTransferOutcome(execution.getId(), 42L, upload, snapshot);

    List<MigrationFileExecutionDetail> collected = details(execution);
    assertEquals(expected, executionRepository.findById(execution.getId()).orElseThrow().getStatus());
    assertEquals(2, collected.size());
    for (MigrationFileExecutionDetail original : exported) {
      MigrationFileExecutionDetail updated = collected.stream()
        .filter(detail -> detail.getId().equals(original.getId())).findFirst().orElseThrow();
      assertEquals(original.getFileName(), updated.getFileName());
      assertEquals(original.getIpaCode(), updated.getIpaCode());
      assertEquals(0L, original.getFileSize());
      assertEquals(5L, updated.getFileSize());
      assertEquals(original.getCreationDate(), updated.getCreationDate());
      assertEquals(10, updated.getNumTotalRows());
      assertEquals((int) accepted, updated.getNumCorrectlyImportedRows());
      assertEquals(original.getFileName().equals(FILE_NAME) ? 123L : 124L, updated.getUploadDetailId());
    }

    transferOutcomeService.recordTransferOutcome(execution.getId(), 42L, upload, snapshot);

    assertEquals(collected, details(execution));
  }

  static Stream<Arguments> puOutcomes() {
    return Stream.of(
      Arguments.of(IngestionFlowFileStatus.COMPLETED, 10L, MigrationFileExecutionStatus.COMPLETED),
      Arguments.of(IngestionFlowFileStatus.WARNING, 8L, MigrationFileExecutionStatus.WARNING),
      Arguments.of(IngestionFlowFileStatus.WARNING, 0L, MigrationFileExecutionStatus.FAILED)
    );
  }

  @Test
  void givenExportedFileWhenTransferOutcomeTimesOutThenPersistsFailedDetailWithoutLosingIpa() throws IOException {
    MigrationFileExecution execution = completeExtraction();
    MigrationFileExecutionDetail original = details(execution).getFirst();
    transferService.markTransferring(execution.getMigrationId(), FILE_TYPE, COMPLETED_AT);
    transferService.recordUploadAccepted(
      execution.getMigrationId(), FILE_TYPE, 42L, UploadsStatusEnum.UPLOADED, COMPLETED_AT
    );

    transferOutcomeService.markFailed(execution.getId(), 42L, "TRANSFER_OUTCOME_TIMEOUT", "timed out");

    MigrationFileExecutionDetail failed = details(execution).getFirst();
    assertEquals(original.getId(), failed.getId());
    assertEquals(original.getIpaCode(), failed.getIpaCode());
    assertEquals(original.getCreationDate(), failed.getCreationDate());
    assertEquals(MigrationFileExecutionDetailStatus.FAILED, failed.getStatus());
    assertEquals(MigrationFileExecutionStatus.FAILED,
      executionRepository.findById(execution.getId()).orElseThrow().getStatus());
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 5L})
  void givenProvisionalSizeWhenRecordTransferOutcomeThenBindsPuSizeAndRejectsSubsequentChanges(long puSize) {
    MigrationFileExecution execution = exportingExecution();
    UUID migrationId = execution.getMigrationId();
    exportService.markExported(migrationId, FILE_TYPE, archives(), COMPLETED_AT);
    transferService.markTransferring(migrationId, FILE_TYPE, COMPLETED_AT);
    transferService.recordUploadAccepted(migrationId, FILE_TYPE, 42L, UploadsStatusEnum.UPLOADED, COMPLETED_AT);
    Uploads upload = new Uploads().uploadId(42L).status(UploadsStatusEnum.PROCESSING).numTotalFiles(1);
    UploadDetails observation = new UploadDetails().uploadId(42L).uploadDetailId(123L)
      .fileName(FILE_NAME).fileSize(puSize).status(IngestionFlowFileStatus.PROCESSING);

    transferOutcomeService.recordTransferOutcome(execution.getId(), 42L, upload, List.of(observation));

    MigrationFileExecutionDetail bound = details(execution).getFirst();
    assertEquals(puSize, bound.getFileSize());
    assertEquals(123L, bound.getUploadDetailId());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, bound.getStatus());
    observation.setFileSize(puSize + 1L);

    UUID executionId = execution.getId();
    List<UploadDetails> snapshot = List.of(observation);
    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> transferOutcomeService.recordTransferOutcome(executionId, 42L, upload, snapshot));

    assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    assertEquals(bound, details(execution).getFirst());
    assertEquals(MigrationFileExecutionStatus.WAITING_PU,
      executionRepository.findById(execution.getId()).orElseThrow().getStatus());
  }

  @Test
  void givenAmbiguousProvisionalFilesWhenRecordTransferOutcomeThenRejectsWithoutBindingDetails() {
    MigrationFileExecution execution = exportingExecution();
    UUID migrationId = execution.getMigrationId();
    List<String> files = List.of("first/" + FILE_NAME, "second/" + FILE_NAME);
    exportService.markExported(migrationId, FILE_TYPE,
      List.of(new ArchiveFile("organizations.zip", files)), COMPLETED_AT);
    transferService.markTransferring(migrationId, FILE_TYPE, COMPLETED_AT);
    transferService.recordUploadAccepted(migrationId, FILE_TYPE, 42L, UploadsStatusEnum.UPLOADED, COMPLETED_AT);
    List<MigrationFileExecutionDetail> original = details(execution);
    Uploads upload = new Uploads().uploadId(42L).status(UploadsStatusEnum.PROCESSING).numTotalFiles(2);
    List<UploadDetails> snapshot = List.of(new UploadDetails().uploadId(42L).uploadDetailId(123L)
      .fileName(FILE_NAME).fileSize(5L).status(IngestionFlowFileStatus.PROCESSING));

    UUID executionId = execution.getId();
    InvalidValueException failure = assertThrows(InvalidValueException.class,
      () -> transferOutcomeService.recordTransferOutcome(executionId, 42L, upload, snapshot));

    assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    assertEquals(original, details(execution));
    assertTrue(details(execution).stream().allMatch(detail -> detail.getUploadDetailId() == null));
  }

  private MigrationFileExecution completeExtraction() throws IOException {
    MigrationFileExecution execution = exportingExecution();
    createArchive(execution, List.of(FILE_NAME));
    return exportService.markExported(execution.getMigrationId(), FILE_TYPE, archives(), COMPLETED_AT);
  }

  private MigrationFileExecution exportingExecution() {
    return executionRepository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(UUID.randomUUID())
      .fileType(FILE_TYPE)
      .status(MigrationFileExecutionStatus.EXPORTING)
      .extractionId(UUID.randomUUID().toString())
      .build());
  }

  private void createArchive(MigrationFileExecution execution, List<String> files) throws IOException {
    Path directory = Files.createDirectory(sharedFolder.resolve(execution.getExtractionId()));
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(directory.resolve("organizations.zip")))) {
      for (String file : files) {
        zip.putNextEntry(new ZipEntry(file));
        zip.write("a\n1\n2".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
  }

  private List<ArchiveFile> archives() {
    return List.of(new ArchiveFile("organizations.zip", List.of(FILE_NAME)));
  }

  private List<MigrationFileExecutionDetail> details(MigrationFileExecution execution) {
    return detailRepository.findAllByMigrationFileExecutionId(execution.getId());
  }
}
