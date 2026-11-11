package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferValidator;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MigrationFileExecutionTransferServiceTest {

  private static final UUID MIGRATION_ID = UUID.fromString("5b15e78a-c2e8-4864-a6c5-5e8e401f160a");
  private static final MigrationFileType FILE_TYPE = MigrationFileType.ORGANIZATIONS;
  private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 10, 55);
  private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 10, 56);

  @Mock
  private MigrationFileExecutionRepository migrationFileExecutionRepositoryMock;

  private MigrationFileExecutionTransferService service;

  @BeforeEach
  void setUp() {
    service = new MigrationFileExecutionTransferService(
      migrationFileExecutionRepositoryMock,
      new MigrationFileExecutionTransferValidator()
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationFileExecutionRepositoryMock);
  }

  @Test
  void givenExportedFileWhenTransferLifecycleCompletesThenPersistsUploadAcceptance() {
    MigrationFileExecution execution = exportedExecution();
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.claimTransfer(
      MIGRATION_ID,
      FILE_TYPE,
      MigrationFileExecutionStatus.EXPORTED,
      MigrationFileExecutionStatus.TRANSFERING,
      STARTED_AT
    )).thenAnswer(invocation -> {
      execution.setStatus(MigrationFileExecutionStatus.TRANSFERING);
      execution.setUploadStartedAt(STARTED_AT);
      return 1;
    });
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution transferring = service.markTransferring(MIGRATION_ID, FILE_TYPE, STARTED_AT);
    MigrationFileExecution waiting = service.recordUploadAccepted(MIGRATION_ID, FILE_TYPE, 42L, COMPLETED_AT);

    assertSame(execution, transferring);
    assertSame(execution, waiting);
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, waiting.getStatus());
    assertEquals(42L, waiting.getUploadId());
    assertEquals(UploadsStatusEnum.UPLOADED, waiting.getUploadStatus());
    assertEquals(STARTED_AT, waiting.getUploadStartedAt());
    assertEquals(COMPLETED_AT, waiting.getUploadCompletedAt());
    assertEquals("extractor-123", waiting.getExtractionId());
    assertEquals("organizations.zip", waiting.getFilePath());
    verify(migrationFileExecutionRepositoryMock, times(3))
      .findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).claimTransfer(
      MIGRATION_ID,
      FILE_TYPE,
      MigrationFileExecutionStatus.EXPORTED,
      MigrationFileExecutionStatus.TRANSFERING,
      STARTED_AT
    );
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenTransferringFileWhenMarkTransferFailedThenPersistsSpecificError() {
    MigrationFileExecution execution = exportedExecution();
    execution.setStatus(MigrationFileExecutionStatus.TRANSFERING);
    execution.setUploadStartedAt(STARTED_AT);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.markTransferFailed(
      MIGRATION_ID, FILE_TYPE, "PU_VALIDATION_ERROR", "PU rejected the archive"
    );

    assertEquals(MigrationFileExecutionStatus.FAILED, result.getStatus());
    assertEquals("PU_VALIDATION_ERROR", result.getUploadErrorCode());
    assertEquals("PU rejected the archive", result.getUploadErrorMsg());
    assertEquals("extractor-123", result.getExtractionId());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenNullUploadIdWhenRecordUploadAcceptedThenRejectsBeforeRepositoryLookup() {
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> service.recordUploadAccepted(MIGRATION_ID, FILE_TYPE, null, COMPLETED_AT)
    );

    assertEquals("INVALID_UPLOAD_RESPONSE", exception.getCode());
  }

  @Test
  void givenLostTransferClaimWhenMarkTransferringThenRejectsDuplicateUpload() {
    MigrationFileExecution execution = exportedExecution();
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.claimTransfer(
      MIGRATION_ID,
      FILE_TYPE,
      MigrationFileExecutionStatus.EXPORTED,
      MigrationFileExecutionStatus.TRANSFERING,
      STARTED_AT
    )).thenReturn(0);

    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> service.markTransferring(MIGRATION_ID, FILE_TYPE, STARTED_AT)
    );

    assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).claimTransfer(
      MIGRATION_ID,
      FILE_TYPE,
      MigrationFileExecutionStatus.EXPORTED,
      MigrationFileExecutionStatus.TRANSFERING,
      STARTED_AT
    );
  }

  private MigrationFileExecution exportedExecution() {
    return MigrationFileExecution.builder()
      .id(UUID.randomUUID())
      .migrationId(MIGRATION_ID)
      .fileType(FILE_TYPE)
      .status(MigrationFileExecutionStatus.EXPORTED)
      .extractionId("extractor-123")
      .filePath("organizations.zip")
      .extractionStartedAt(STARTED_AT.minusMinutes(1))
      .extractionCompletedAt(STARTED_AT)
      .build();
  }
}
