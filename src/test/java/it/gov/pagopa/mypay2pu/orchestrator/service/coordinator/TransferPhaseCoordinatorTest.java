package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFacadeService;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationPlanExecutorService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferPhaseCoordinatorTest {

  private static final UUID MIGRATION_ID = UUID.randomUUID();

  @Mock
  private MigrationFacadeService migrationFacadeServiceMock;
  @Mock
  private MigrationFileExecutionRepository migrationFileExecutionRepositoryMock;

  private TransferPhaseCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator = new TransferPhaseCoordinator(
      migrationFacadeServiceMock,
      migrationFileExecutionRepositoryMock,
      new MigrationPlanExecutorService(),
      1,
      3
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      migrationFacadeServiceMock,
      migrationFileExecutionRepositoryMock
    );
  }

  @Test
  void givenParentExportedWhenStartThenChildTransfersAfterParentCompletes() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.WAITING_PU
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.COMPLETED
    )));

    coordinator.start(execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS
    )).join();

    InOrder inOrder = inOrder(migrationFileExecutionRepositoryMock, migrationFacadeServiceMock);
    inOrder.verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    ArgumentCaptor<MigrationRequestDto> requestCaptor = ArgumentCaptor.forClass(MigrationRequestDto.class);
    inOrder.verify(migrationFacadeServiceMock).execute(requestCaptor.capture());
    assertTransferRequest(requestCaptor.getValue(), MigrationFileType.DEBT_POSITIONS_TYPE);
    inOrder.verify(migrationFileExecutionRepositoryMock, times(2)).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    inOrder.verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
    inOrder.verify(migrationFacadeServiceMock).execute(requestCaptor.capture());
    assertTransferRequest(requestCaptor.getValue(), MigrationFileType.ORGANIZATIONS);
    inOrder.verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
  }

  @Test
  void givenParentWarningWhenStartThenChildTransferContinues() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileExecutionStatus.WARNING
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileExecutionStatus.COMPLETED
    )));

    coordinator.start(execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.DEBT_POSITIONS
    )).join();

    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORGANIZATIONS))
    ));
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG))
    ));
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.DEBT_POSITIONS))
    ));
  }

  @Test
  void givenParentFailedWhenStartThenDependentRemainsPending() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.FAILED
    )));

    coordinator.start(execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS
    )).join();

    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
  }

  @Test
  void givenIndependentBranchWhenAnotherParentFailsThenIndependentBranchTransfers() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileExecutionStatus.FAILED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORG_SIL_SERVICES
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORG_SIL_SERVICES,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORG_SIL_SERVICES,
      MigrationFileExecutionStatus.COMPLETED
    )));

    coordinator.start(execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.ORG_SIL_SERVICES,
      MigrationFileType.DEBT_POSITIONS_PAID,
      MigrationFileType.ASSESSMENTS
    )).join();

    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORG_SIL_SERVICES))
    ));
  }

  @Test
  void givenParentNeverCompletesWhenStartThenStartFailsAfterPollingTimeout() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.WAITING_PU
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.WAITING_PU
    )));

    MigrationExecution migrationExecution = execution(MigrationFileType.DEBT_POSITIONS_TYPE);
    IllegalStateException exception = assertThrows(
      IllegalStateException.class,
      () -> coordinator.start(migrationExecution)
    );

    assertEquals(
      "Timed out waiting for PU completion of file type DEBT_POSITIONS_TYPE for migration %s after 3 attempts"
        .formatted(MIGRATION_ID),
      exception.getMessage()
    );
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.DEBT_POSITIONS_TYPE))
    ));
    verify(migrationFileExecutionRepositoryMock).save(argThat(
      fileExecution -> fileExecution.getFileType() == MigrationFileType.DEBT_POSITIONS_TYPE
        && fileExecution.getStatus() == MigrationFileExecutionStatus.FAILED
        && "TRANSFER_PU_TIMEOUT".equals(fileExecution.getUploadErrorCode())
    ));
  }

  @Test
  void givenTransferAlreadyStartedWhenStartThenResumePollingWithoutResubmitting() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.TRANSFERING
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.WAITING_PU
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.WARNING
    )));
    MigrationExecution migrationExecution = execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS
    );

    coordinator.start(migrationExecution).join();

    verify(migrationFileExecutionRepositoryMock, times(2)).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    verify(migrationFileExecutionRepositoryMock, times(2)).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
  }

  @Test
  void givenPendingFileWhenStartThenRejectTransferAsNotReady() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.PENDING
    )));
    MigrationExecution migrationExecution = execution(MigrationFileType.DEBT_POSITIONS_TYPE);

    IllegalStateException exception = assertThrows(
      IllegalStateException.class,
      () -> coordinator.start(migrationExecution)
    );

    assertEquals(
      "File type DEBT_POSITIONS_TYPE for migration %s is not ready for transfer (status=PENDING)"
        .formatted(MIGRATION_ID),
      exception.getMessage()
    );
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    verify(migrationFileExecutionRepositoryMock).save(argThat(
      fileExecution -> fileExecution.getFileType() == MigrationFileType.DEBT_POSITIONS_TYPE
        && fileExecution.getStatus() == MigrationFileExecutionStatus.FAILED
        && "TRANSFER_FILE_NOT_READY".equals(fileExecution.getUploadErrorCode())
    ));
  }

  @Test
  void givenMissingFileExecutionWhenStartThenFailWithFileTypeAndMigration() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.empty());
    MigrationExecution migrationExecution = execution(MigrationFileType.DEBT_POSITIONS_TYPE);

    IllegalStateException exception = assertThrows(
      IllegalStateException.class,
      () -> coordinator.start(migrationExecution)
    );

    assertEquals(
      "File execution for type DEBT_POSITIONS_TYPE and migration %s was not found".formatted(MIGRATION_ID),
      exception.getMessage()
    );
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
  }

  @Test
  void givenChildTransferFailsWhenStartThenPropagateRuntimeFailure() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.EXPORTED
    )));
    doThrow(new IllegalStateException("upload failed"))
      .when(migrationFacadeServiceMock)
      .execute(argThat(
        request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORGANIZATIONS))
      ));
    MigrationExecution migrationExecution = execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS
    );

    IllegalStateException exception = assertThrows(
      IllegalStateException.class,
      () -> coordinator.start(migrationExecution)
    );

    assertEquals("upload failed", exception.getMessage());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORGANIZATIONS))
    ));
    verify(migrationFileExecutionRepositoryMock).save(argThat(
      fileExecution -> fileExecution.getFileType() == MigrationFileType.ORGANIZATIONS
        && fileExecution.getStatus() == MigrationFileExecutionStatus.FAILED
        && "TRANSFER_FAILED".equals(fileExecution.getUploadErrorCode())
    ));
  }

  @Test
  void givenChildTransferThrowsErrorWhenStartThenPropagateError() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileExecutionStatus.EXPORTED
    )));
    doThrow(new AssertionError("fatal upload error"))
      .when(migrationFacadeServiceMock)
      .execute(argThat(
        request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORGANIZATIONS))
      ));
    MigrationExecution migrationExecution = execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS
    );

    AssertionError error = assertThrows(
      AssertionError.class,
      () -> coordinator.start(migrationExecution)
    );

    assertEquals("fatal upload error", error.getMessage());
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.ORGANIZATIONS))
    ));
  }

  @Test
  void givenCheckedCauseWhenStartThenPreserveCompletionException() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.COMPLETED
    )));
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    )).thenThrow(new CompletionException(new IOException("checked failure")));
    MigrationExecution migrationExecution = execution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS
    );

    CompletionException exception = assertThrows(
      CompletionException.class,
      () -> coordinator.start(migrationExecution)
    );

    assertTrue(exception.getCause() instanceof IOException);
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    verify(migrationFileExecutionRepositoryMock).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS
    );
  }

  @Test
  void givenInterruptedPollingWhenStartThenRestoreInterruptAndFail() {
    when(migrationFileExecutionRepositoryMock.findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    )).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.EXPORTED
    ))).thenReturn(Optional.of(fileExecution(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileExecutionStatus.WAITING_PU
    )));
    MigrationExecution migrationExecution = execution(MigrationFileType.DEBT_POSITIONS_TYPE);
    boolean interruptedBeforeTest = Thread.interrupted();

    try {
      Thread.currentThread().interrupt();
      IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> coordinator.start(migrationExecution)
      );

      assertTrue(Thread.currentThread().isInterrupted());
      assertEquals(
        "Interrupted while waiting for PU completion of file type DEBT_POSITIONS_TYPE for migration %s"
          .formatted(MIGRATION_ID),
        exception.getMessage()
      );
    } finally {
      Thread.interrupted();
      if (interruptedBeforeTest) {
        Thread.currentThread().interrupt();
      }
    }

    verify(migrationFileExecutionRepositoryMock, times(2)).findByMigrationIdAndFileType(
      MIGRATION_ID,
      MigrationFileType.DEBT_POSITIONS_TYPE
    );
    verify(migrationFacadeServiceMock).execute(argThat(
      request -> request.getFileTypesToInclude().equals(List.of(MigrationFileType.DEBT_POSITIONS_TYPE))
    ));
    verify(migrationFileExecutionRepositoryMock).save(argThat(
      fileExecution -> fileExecution.getFileType() == MigrationFileType.DEBT_POSITIONS_TYPE
        && fileExecution.getStatus() == MigrationFileExecutionStatus.FAILED
        && "TRANSFER_PU_INTERRUPTED".equals(fileExecution.getUploadErrorCode())
    ));
  }

  @Test
  void givenNonPositivePollingIntervalWhenConstructThenRejectConfiguration() {
    MigrationPlanExecutorService planExecutorService = new MigrationPlanExecutorService();
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> new TransferPhaseCoordinator(
        migrationFacadeServiceMock,
        migrationFileExecutionRepositoryMock,
        planExecutorService,
        0,
        3
      )
    );

    assertEquals("Transfer polling interval must be greater than zero", exception.getMessage());
  }

  @Test
  void givenNonPositivePollingMaxAttemptsWhenConstructThenRejectConfiguration() {
    MigrationPlanExecutorService planExecutorService = new MigrationPlanExecutorService();
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> new TransferPhaseCoordinator(
        migrationFacadeServiceMock,
        migrationFileExecutionRepositoryMock,
        planExecutorService,
        1,
        0
      )
    );

    assertEquals("Transfer polling max attempts must be greater than zero", exception.getMessage());
  }

  private MigrationExecution execution(MigrationFileType... fileTypes) {
    return MigrationExecution.builder()
      .id(MIGRATION_ID)
      .action(MigrationAction.TRANSFER)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipacodes(List.of("80000000000"))
      .fileTypes(List.of(fileTypes))
      .build();
  }

  private MigrationFileExecution fileExecution(
    MigrationFileType fileType,
    MigrationFileExecutionStatus status
  ) {
    return MigrationFileExecution.builder()
      .migrationId(MIGRATION_ID)
      .fileType(fileType)
      .status(status)
      .build();
  }

  private void assertTransferRequest(MigrationRequestDto request, MigrationFileType fileType) {
    assertEquals(MigrationAction.TRANSFER, request.getAction());
    assertEquals(OrchestrationMode.FULL, request.getOrchestrationMode());
    assertEquals(MIGRATION_ID, request.getMigrationId());
    assertEquals(List.of("80000000000"), request.getIpaCodes());
    assertEquals(List.of(fileType), request.getFileTypesToInclude());
  }
}
