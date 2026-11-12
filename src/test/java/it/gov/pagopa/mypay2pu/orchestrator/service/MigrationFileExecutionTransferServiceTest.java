package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
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

@ExtendWith(MockitoExtension.class)
class MigrationFileExecutionTransferServiceTest {

  private static final UUID MIGRATION_ID = UUID.randomUUID();
  private static final MigrationFileType FILE_TYPE = MigrationFileType.ORGANIZATIONS;
  private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 12, 0);
  private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 12, 1);

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
  void givenExportedExecutionWhenMarkTransferringThenAtomicallyClaimsIt() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.claimTransfer(
      MIGRATION_ID,
      FILE_TYPE,
      STARTED_AT
    )).thenReturn(1);

    MigrationFileExecution result = service.markTransferring(MIGRATION_ID, FILE_TYPE, STARTED_AT);

    assertSame(execution, result);
    assertEquals(MigrationFileExecutionStatus.TRANSFERING, result.getStatus());
    assertEquals(STARTED_AT, result.getUploadStartedAt());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).claimTransfer(
      MIGRATION_ID,
      FILE_TYPE,
      STARTED_AT
    );
  }

  @Test
  void givenTransferringExecutionWhenRecordUploadAcceptedThenPersistsWaitingPu() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.recordUploadAccepted(
      MIGRATION_ID,
      FILE_TYPE,
      42L,
      UploadsStatusEnum.UPLOADED,
      COMPLETED_AT
    );

    assertSame(execution, result);
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, result.getStatus());
    assertEquals(42L, result.getUploadId());
    assertEquals(UploadsStatusEnum.UPLOADED, result.getUploadStatus());
    assertEquals(COMPLETED_AT, result.getUploadCompletedAt());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenExistingExecutionWhenGetRequiredThenReturnsExecution() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    MigrationFileExecution result = service.getRequired(MIGRATION_ID, FILE_TYPE);

    assertSame(execution, result);
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenMissingExecutionWhenGetRequiredThenThrowsNotFoundException() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(
      NotFoundException.class,
      () -> service.getRequired(MIGRATION_ID, FILE_TYPE)
    );

    assertEquals("MIGRATION_FILE_EXECUTION_NOT_FOUND", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenMissingExecutionWhenMarkTransferringThenThrowsNotFoundException() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.empty());

    assertThrows(
      NotFoundException.class,
      () -> service.markTransferring(MIGRATION_ID, FILE_TYPE, STARTED_AT)
    );

    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenClaimedExecutionWhenMarkTransferringThenThrowsConflictException() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.claimTransfer(
      MIGRATION_ID,
      FILE_TYPE,
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
      STARTED_AT
    );
  }

  @Test
  void givenInvalidAcceptedUploadWhenRecordUploadAcceptedThenDoesNotPersist() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> service.recordUploadAccepted(
        MIGRATION_ID,
        FILE_TYPE,
        0L,
        UploadsStatusEnum.UPLOADED,
        COMPLETED_AT
      )
    );

    assertEquals("INVALID_UPLOAD_ID", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenTransferInProgressWhenMarkTransferFailedThenPersistsFailure() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.markTransferFailed(
      MIGRATION_ID,
      FILE_TYPE,
      "UPLOAD_FAILED",
      "PU rejected the upload"
    );

    assertSame(execution, result);
    assertEquals(MigrationFileExecutionStatus.FAILED, result.getStatus());
    assertEquals("UPLOAD_FAILED", result.getUploadErrorCode());
    assertEquals("PU rejected the upload", result.getUploadErrorMsg());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenMissingExecutionWhenMarkTransferFailedThenThrowsNotFoundException() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE))
      .thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(
      NotFoundException.class,
      () -> service.markTransferFailed(MIGRATION_ID, FILE_TYPE, "UPLOAD_FAILED", "failure")
    );

    assertEquals("MIGRATION_FILE_EXECUTION_NOT_FOUND", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(MIGRATION_ID, FILE_TYPE);
  }

  private MigrationFileExecution fileExecution(MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder()
      .id(UUID.randomUUID())
      .migrationId(MIGRATION_ID)
      .fileType(FILE_TYPE)
      .status(status)
      .extractionId("extractor-123")
      .filePath("organizations.zip")
      .build();
  }
}
