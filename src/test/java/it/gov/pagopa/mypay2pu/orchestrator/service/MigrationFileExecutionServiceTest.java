package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationFileExecutionServiceTest {

  private static final UUID MIGRATION_ID = UUID.randomUUID();
  private static final MigrationFileType FILE_TYPE = MigrationFileType.ORGANIZATIONS;
  private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, Month.OCTOBER, 27, 16, 0);
  private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, Month.OCTOBER, 27, 16, 30);

  @Mock
  private MigrationFileExecutionRepository migrationFileExecutionRepositoryMock;

  private MigrationFileExecutionService service;

  @BeforeEach
  void setUp() {
    service = new MigrationFileExecutionService(migrationFileExecutionRepositoryMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationFileExecutionRepositoryMock);
  }

  @Test
  void givenNoFileExecutionWhenEnsurePendingThenCreatesPendingRow() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.empty());
    when(migrationFileExecutionRepositoryMock.save(org.mockito.ArgumentMatchers.isA(MigrationFileExecution.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    MigrationFileExecution result = service.ensurePending(MIGRATION_ID, FILE_TYPE);

    assertEquals(MIGRATION_ID, result.getMigrationId());
    assertEquals(FILE_TYPE, result.getFileType());
    assertEquals(MigrationFileExecutionStatus.PENDING, result.getStatus());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock)
      .save(org.mockito.ArgumentMatchers.isA(MigrationFileExecution.class));
  }

  @Test
  void givenExistingFileExecutionWhenEnsurePendingThenReturnsExistingRow() {
    MigrationFileExecution existing = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTING);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(existing));

    MigrationFileExecution result = service.ensurePending(MIGRATION_ID, FILE_TYPE);

    assertSame(existing, result);
    assertEquals(MigrationFileExecutionStatus.EXPORTING, result.getStatus());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenPendingFileExecutionWhenExportLifecycleCompletesThenPersistsEachTransition() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.PENDING);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution exporting = service.markExporting(MIGRATION_ID, FILE_TYPE, STARTED_AT);
    MigrationFileExecution accepted = service.recordAccepted(MIGRATION_ID, FILE_TYPE, "extractor-123");
    MigrationFileExecution exported = service.markExported(
      MIGRATION_ID, FILE_TYPE, "archive/organizations.zip", COMPLETED_AT
    );

    assertSame(execution, exporting);
    assertSame(execution, accepted);
    assertSame(execution, exported);
    assertEquals(MigrationFileExecutionStatus.EXPORTED, exported.getStatus());
    assertEquals("extractor-123", exported.getExtractionId());
    assertEquals("archive/organizations.zip", exported.getFilePath());
    assertEquals(STARTED_AT, exported.getExtractionStartedAt());
    assertEquals(COMPLETED_AT, exported.getExtractionCompletedAt());
    verify(migrationFileExecutionRepositoryMock, times(3)).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock, times(3)).save(execution);
  }

  @Test
  void givenPendingFileExecutionWithExtractionIdWhenMarkExportingThenRejectsTransition() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.PENDING);
    execution.setExtractionId("extractor-123");
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.markExporting(MIGRATION_ID, FILE_TYPE, STARTED_AT)
    );
  }

  @Test
  void givenExportingFileExecutionWhenRecordAcceptedThenKeepsSameExtractionId() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTING);
    execution.setExtractionId("extractor-123");
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.recordAccepted(MIGRATION_ID, FILE_TYPE, "extractor-123");

    assertSame(execution, result);
    assertEquals("extractor-123", result.getExtractionId());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenExportingFileExecutionWithExtractionIdWhenRecordAcceptedThenRejectsDifferentId() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTING);
    execution.setExtractionId("extractor-123");
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.recordAccepted(MIGRATION_ID, FILE_TYPE, "extractor-456")
    );
  }

  @Test
  void givenNullExtractionIdWhenRecordAcceptedThenRejectsBeforeRepositoryLookup() {
    assertThrows(
      IllegalArgumentException.class,
      () -> service.recordAccepted(MIGRATION_ID, FILE_TYPE, null)
    );
  }

  @Test
  void givenExportedFileExecutionWithMatchingResultWhenMarkExportedThenKeepsExistingResult() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTED);
    execution.setFilePath("archive/organizations.zip");
    execution.setExtractionCompletedAt(COMPLETED_AT);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.markExported(
      MIGRATION_ID, FILE_TYPE, "archive/organizations.zip", COMPLETED_AT
    );

    assertSame(execution, result);
    assertEquals("archive/organizations.zip", result.getFilePath());
    assertEquals(COMPLETED_AT, result.getExtractionCompletedAt());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenExportedFileExecutionWithDifferentFilePathWhenMarkExportedThenRejectsTransition() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTED);
    execution.setFilePath("archive/previous.zip");
    execution.setExtractionCompletedAt(COMPLETED_AT);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.markExported(MIGRATION_ID, FILE_TYPE, "archive/organizations.zip", COMPLETED_AT)
    );
  }

  @Test
  void givenExportedFileExecutionWithDifferentCompletionTimeWhenMarkExportedThenRejectsTransition() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTED);
    execution.setFilePath("archive/organizations.zip");
    execution.setExtractionCompletedAt(STARTED_AT);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.markExported(MIGRATION_ID, FILE_TYPE, "archive/organizations.zip", COMPLETED_AT)
    );
  }

  @Test
  void givenExportingFileExecutionWithoutExtractionIdWhenMarkExportedThenRejectsTransition() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTING);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.markExported(MIGRATION_ID, FILE_TYPE, "archive/organizations.zip", COMPLETED_AT)
    );
  }

  @Test
  void givenExportingFileExecutionWithBlankExtractionIdWhenMarkExportedThenRejectsTransition() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTING);
    execution.setExtractionId(" ");
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.markExported(MIGRATION_ID, FILE_TYPE, "archive/organizations.zip", COMPLETED_AT)
    );
  }

  @Test
  void givenAcceptedExtractionWhenMarkFailedThenPersistsFailureDetails() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTING);
    execution.setExtractionId("extractor-123");
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.markFailed(MIGRATION_ID, FILE_TYPE, "EXTRACTOR_ERROR", "Extraction failed");

    assertEquals(MigrationFileExecutionStatus.FAILED, result.getStatus());
    assertEquals("EXTRACTOR_ERROR", result.getExtractionErrorCode());
    assertEquals("Extraction failed", result.getExtractionErrorMsg());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenFailedFileExecutionWithMatchingErrorsWhenMarkFailedThenKeepsExistingFailure() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.FAILED);
    execution.setExtractionErrorCode("EXTRACTOR_ERROR");
    execution.setExtractionErrorMsg("Extraction failed");
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.markFailed(
      MIGRATION_ID, FILE_TYPE, "EXTRACTOR_ERROR", "Extraction failed"
    );

    assertSame(execution, result);
    assertEquals(MigrationFileExecutionStatus.FAILED, result.getStatus());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenPendingFileExecutionWhenMarkFailedThenPersistsFailureDetails() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.PENDING);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.markFailed(
      MIGRATION_ID, FILE_TYPE, "EXTRACTOR_ERROR", "Extraction failed"
    );

    assertEquals(MigrationFileExecutionStatus.FAILED, result.getStatus());
    assertEquals("EXTRACTOR_ERROR", result.getExtractionErrorCode());
    assertEquals("Extraction failed", result.getExtractionErrorMsg());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenFailedFileExecutionWithDifferentErrorCodeWhenMarkFailedThenRejectsTransition() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.FAILED);
    execution.setExtractionErrorCode("PREVIOUS_ERROR");
    execution.setExtractionErrorMsg("Extraction failed");
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.markFailed(MIGRATION_ID, FILE_TYPE, "EXTRACTOR_ERROR", "Extraction failed")
    );
  }

  @Test
  void givenFailedFileExecutionWithDifferentErrorMessageWhenMarkFailedThenRejectsTransition() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.FAILED);
    execution.setExtractionErrorCode("EXTRACTOR_ERROR");
    execution.setExtractionErrorMsg("Previous message");
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.markFailed(MIGRATION_ID, FILE_TYPE, "EXTRACTOR_ERROR", "Extraction failed")
    );
  }

  @Test
  void givenExportedFileExecutionWhenMarkFailedThenRejectsInvalidTransition() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTED);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.markFailed(MIGRATION_ID, FILE_TYPE, "EXTRACTOR_ERROR", "Extraction failed")
    );
  }

  @Test
  void givenExportingFileExecutionWhenMarkExportingThenRejectsInvalidStatus() {
    MigrationFileExecution execution = newMigrationFileExecution(MigrationFileExecutionStatus.EXPORTING);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    assertThrows(
      IllegalStateException.class,
      () -> service.markExporting(MIGRATION_ID, FILE_TYPE, STARTED_AT)
    );
  }

  @Test
  void givenMissingFileExecutionWhenMarkExportingThenThrowsNotFoundState() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.empty());

    assertThrows(
      IllegalStateException.class,
      () -> service.markExporting(MIGRATION_ID, FILE_TYPE, STARTED_AT)
    );
  }

  @Test
  void givenBlankExtractionIdWhenRecordAcceptedThenRejectsWithoutTransaction() {
    assertThrows(
      IllegalArgumentException.class,
      () -> service.recordAccepted(MIGRATION_ID, FILE_TYPE, " ")
    );
  }

  private MigrationFileExecution newMigrationFileExecution(MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder()
      .id(UUID.randomUUID())
      .migrationId(MIGRATION_ID)
      .fileType(FILE_TYPE)
      .status(status)
      .build();
  }

}
