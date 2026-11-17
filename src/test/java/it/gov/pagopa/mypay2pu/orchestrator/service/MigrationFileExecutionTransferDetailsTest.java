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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

  @Test
  void givenExtractedFileWhenRecordUploadAcceptedThenUpdatesOnlyExistingDetailStatus() throws IOException {
    MigrationFileExecution execution = completeExtraction();
    MigrationFileExecutionDetail exportedDetail = details(execution).getFirst();
    assertEquals(MigrationFileExecutionDetailStatus.EXPORTED, exportedDetail.getStatus());
    assertEquals("organizations.csv", exportedDetail.getFileName());
    assertEquals(5L, exportedDetail.getFileSize());
    assertNull(exportedDetail.getUploadDetailId());
    assertNull(exportedDetail.getNumTotalRows());

    transferService.markTransferring(execution.getMigrationId(), FILE_TYPE, COMPLETED_AT);
    assertEquals(MigrationFileExecutionDetailStatus.TRANSFERING, details(execution).getFirst().getStatus());

    transferService.recordUploadAccepted(
      execution.getMigrationId(), FILE_TYPE, 42L, UploadsStatusEnum.UPLOADED, COMPLETED_AT
    );

    List<MigrationFileExecutionDetail> acceptedDetails = details(execution);
    assertEquals(1, acceptedDetails.size());
    MigrationFileExecutionDetail acceptedDetail = acceptedDetails.getFirst();
    assertEquals(exportedDetail.getId(), acceptedDetail.getId());
    assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, acceptedDetail.getStatus());
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
  }

  @Test
  void givenMissingZipEntryWhenMarkExportedThenRollsBackDetailsAndCompletion() throws IOException {
    MigrationFileExecution execution = exportingExecution();
    UUID migrationId = execution.getMigrationId();
    createArchive(execution, List.of("organizations.csv"));
    List<ArchiveFile> invalidArchives = List.of(
      new ArchiveFile("organizations.zip", List.of("organizations.csv", "missing.csv"))
    );

    assertThrows(InvalidValueException.class, () -> exportService.markExported(
      migrationId, FILE_TYPE, invalidArchives, COMPLETED_AT
    ));

    assertTrue(details(execution).isEmpty());
    assertEquals(MigrationFileExecutionStatus.EXPORTING,
      executionRepository.findById(execution.getId()).orElseThrow().getStatus());
  }

  @Test
  void givenMultipleExtractedFilesWhenMarkExportedThenCreatesOneDetailPerFile() throws IOException {
    MigrationFileExecution execution = exportingExecution();
    List<String> files = List.of("part-1.csv", "part-2.csv");
    createArchive(execution, files);

    exportService.markExported(execution.getMigrationId(), FILE_TYPE, List.of(
      new ArchiveFile("organizations.zip", files)
    ), COMPLETED_AT);

    List<MigrationFileExecutionDetail> exportedDetails = details(execution);
    assertEquals(2, exportedDetails.size());
    assertEquals(Set.of("part-1.csv", "part-2.csv"),
      exportedDetails.stream().map(MigrationFileExecutionDetail::getFileName).collect(Collectors.toSet()));
    exportedDetails.forEach(detail -> {
      assertEquals(5L, detail.getFileSize());
      assertEquals(MigrationFileExecutionDetailStatus.EXPORTED, detail.getStatus());
    });
  }

  @Test
  void givenDetailPersistenceFailureWhenMarkExportedThenRollsBackCompletionAndAllDetails() throws IOException {
    MigrationFileExecution execution = exportingExecution();
    UUID migrationId = execution.getMigrationId();
    String longFileName = "a".repeat(300) + ".csv";
    List<String> files = List.of("valid.csv", longFileName);
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

  private MigrationFileExecution completeExtraction() throws IOException {
    MigrationFileExecution execution = exportingExecution();
    createArchive(execution, List.of("organizations.csv"));
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
    return List.of(new ArchiveFile("organizations.zip", List.of("organizations.csv")));
  }

  private List<MigrationFileExecutionDetail> details(MigrationFileExecution execution) {
    return detailRepository.findAllByMigrationFileExecutionId(execution.getId());
  }
}
