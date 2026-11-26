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
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferValidator;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;
import java.util.List;
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
  private static final UUID EXECUTION_ID = UUID.randomUUID();
  private static final MigrationFileType FILE_TYPE = MigrationFileType.ORGANIZATIONS;
  private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 12, 0);
  private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 12, 1);

  @Mock
  private MigrationFileExecutionRepository migrationFileExecutionRepositoryMock;
  @Mock
  private MigrationFileExecutionDetailRepository migrationFileExecutionDetailRepositoryMock;

  private MigrationFileExecutionTransferService service;

  @BeforeEach
  void setUp() {
    service = new MigrationFileExecutionTransferService(
      migrationFileExecutionRepositoryMock,
      migrationFileExecutionDetailRepositoryMock,
      new MigrationFileExecutionTransferValidator()
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      migrationFileExecutionRepositoryMock,
      migrationFileExecutionDetailRepositoryMock
    );
  }

  @Test
  void givenExportedExecutionWhenMarkTransferringThenAtomicallyClaimsIt() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    List<MigrationFileExecutionDetail> details = fileDetails(
      execution.getId(), MigrationFileExecutionDetailStatus.EXPORTED
    );
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(execution.getId()))
      .thenReturn(details);
    when(migrationFileExecutionRepositoryMock.findById(EXECUTION_ID))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.claimTransfer(
      execution.getId(),
      STARTED_AT
    )).thenReturn(1);

    MigrationFileExecution result = service.markTransferring(EXECUTION_ID, STARTED_AT);

    assertSame(execution, result);
    assertEquals(MigrationFileExecutionStatus.TRANSFERING, result.getStatus());
    assertEquals(STARTED_AT, result.getUploadStartedAt());
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(execution.getId());
    details.forEach(detail -> assertEquals(MigrationFileExecutionDetailStatus.TRANSFERING, detail.getStatus()));
    verify(migrationFileExecutionDetailRepositoryMock).saveAll(details);
    verify(migrationFileExecutionRepositoryMock).findById(EXECUTION_ID);
    verify(migrationFileExecutionRepositoryMock).claimTransfer(
      execution.getId(),
      STARTED_AT
    );
  }

  @Test
  void givenTransferringExecutionWhenRecordUploadAcceptedThenPersistsWaitingPu() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);
    List<MigrationFileExecutionDetail> details = fileDetails(
      execution.getId(), MigrationFileExecutionDetailStatus.TRANSFERING
    );
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(execution.getId()))
      .thenReturn(details);
    when(migrationFileExecutionRepositoryMock.findById(EXECUTION_ID))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.recordUploadAccepted(
      EXECUTION_ID, 42L,
      UploadsStatusEnum.UPLOADED,
      COMPLETED_AT
    );

    assertSame(execution, result);
    assertEquals(MigrationFileExecutionStatus.WAITING_PU, result.getStatus());
    assertEquals(42L, result.getUploadId());
    assertEquals(UploadsStatusEnum.UPLOADED, result.getUploadStatus());
    assertEquals(COMPLETED_AT, result.getUploadCompletedAt());
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(execution.getId());
    details.forEach(detail -> assertEquals(MigrationFileExecutionDetailStatus.WAITING_PU, detail.getStatus()));
    verify(migrationFileExecutionDetailRepositoryMock).saveAll(details);
    verify(migrationFileExecutionRepositoryMock).findById(EXECUTION_ID);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenExecutionsWhenGetRequiredThenReturnsAll() {
    MigrationFileExecution first = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    MigrationFileExecution second = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    second.setId(UUID.randomUUID());
    List<MigrationFileExecution> executions = List.of(first, second);
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdAndFileTypeOrderByCreationDateAscIdAsc(MIGRATION_ID, FILE_TYPE))
      .thenReturn(executions);

    assertEquals(executions, service.getRequired(MIGRATION_ID, FILE_TYPE));
    verify(migrationFileExecutionRepositoryMock).findAllByMigrationIdAndFileTypeOrderByCreationDateAscIdAsc(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenMissingExecutionWhenGetRequiredThenThrowsNotFoundException() {
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdAndFileTypeOrderByCreationDateAscIdAsc(MIGRATION_ID, FILE_TYPE))
      .thenReturn(List.of());

    NotFoundException exception = assertThrows(
      NotFoundException.class,
      () -> service.getRequired(MIGRATION_ID, FILE_TYPE)
    );

    assertEquals("MIGRATION_FILE_EXECUTION_NOT_FOUND", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findAllByMigrationIdAndFileTypeOrderByCreationDateAscIdAsc(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenMissingExecutionWhenMarkTransferringThenThrowsNotFoundException() {
    when(migrationFileExecutionRepositoryMock.findById(EXECUTION_ID))
      .thenReturn(Optional.empty());

    assertThrows(
      NotFoundException.class,
      () -> service.markTransferring(EXECUTION_ID, STARTED_AT)
    );

    verify(migrationFileExecutionRepositoryMock).findById(EXECUTION_ID);
  }

  @Test
  void givenClaimedExecutionWhenMarkTransferringThenThrowsConflictException() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.EXPORTED);
    when(migrationFileExecutionRepositoryMock.findById(EXECUTION_ID))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.claimTransfer(
      execution.getId(),
      STARTED_AT
    )).thenReturn(0);

    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> service.markTransferring(EXECUTION_ID, STARTED_AT)
    );

    assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findById(EXECUTION_ID);
    verify(migrationFileExecutionRepositoryMock).claimTransfer(
      execution.getId(),
      STARTED_AT
    );
  }

  @Test
  void givenInvalidAcceptedUploadWhenRecordUploadAcceptedThenDoesNotPersist() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);
    when(migrationFileExecutionRepositoryMock.findById(EXECUTION_ID))
      .thenReturn(Optional.of(execution));

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> service.recordUploadAccepted(
        EXECUTION_ID, 0L,
        UploadsStatusEnum.UPLOADED,
        COMPLETED_AT
      )
    );

    assertEquals("INVALID_UPLOAD_ID", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findById(EXECUTION_ID);
  }

  @Test
  void givenTransferInProgressWhenMarkTransferFailedThenPersistsFailure() {
    MigrationFileExecution execution = fileExecution(MigrationFileExecutionStatus.TRANSFERING);
    List<MigrationFileExecutionDetail> details = fileDetails(
      execution.getId(), MigrationFileExecutionDetailStatus.TRANSFERING
    );
    when(migrationFileExecutionDetailRepositoryMock.findAllByMigrationFileExecutionId(execution.getId()))
      .thenReturn(details);
    when(migrationFileExecutionRepositoryMock.findById(EXECUTION_ID))
      .thenReturn(Optional.of(execution));
    when(migrationFileExecutionRepositoryMock.save(execution)).thenReturn(execution);

    MigrationFileExecution result = service.markTransferFailed(
      EXECUTION_ID, "UPLOAD_FAILED",
      "PU rejected the upload"
    );

    assertSame(execution, result);
    assertEquals(MigrationFileExecutionStatus.FAILED, result.getStatus());
    assertEquals("UPLOAD_FAILED", result.getUploadErrorCode());
    assertEquals("PU rejected the upload", result.getUploadErrorMsg());
    verify(migrationFileExecutionDetailRepositoryMock).findAllByMigrationFileExecutionId(execution.getId());
    details.forEach(detail -> assertEquals(MigrationFileExecutionDetailStatus.FAILED, detail.getStatus()));
    verify(migrationFileExecutionDetailRepositoryMock).saveAll(details);
    verify(migrationFileExecutionRepositoryMock).findById(EXECUTION_ID);
    verify(migrationFileExecutionRepositoryMock).save(execution);
  }

  @Test
  void givenMissingExecutionWhenMarkTransferFailedThenThrowsNotFoundException() {
    when(migrationFileExecutionRepositoryMock.findById(EXECUTION_ID))
      .thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(
      NotFoundException.class,
      () -> service.markTransferFailed(EXECUTION_ID, "UPLOAD_FAILED", "failure")
    );

    assertEquals("MIGRATION_FILE_EXECUTION_NOT_FOUND", exception.getCode());
    verify(migrationFileExecutionRepositoryMock).findById(EXECUTION_ID);
  }

  private MigrationFileExecution fileExecution(MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder()
      .id(EXECUTION_ID)
      .migrationId(MIGRATION_ID)
      .fileType(FILE_TYPE)
      .status(status)
      .extractionId("extractor-123")
      .filePath("organizations.zip")
      .build();
  }

  private List<MigrationFileExecutionDetail> fileDetails(
    UUID fileExecutionId, MigrationFileExecutionDetailStatus status
  ) {
    return List.of("part-1.csv", "part-2.csv").stream()
      .<MigrationFileExecutionDetail>map(fileName -> MigrationFileExecutionDetail.builder()
        .id(UUID.randomUUID())
        .migrationFileExecutionId(fileExecutionId)
        .fileName(fileName)
        .fileSize(500L)
        .status(status)
        .build())
      .toList();
  }
}
