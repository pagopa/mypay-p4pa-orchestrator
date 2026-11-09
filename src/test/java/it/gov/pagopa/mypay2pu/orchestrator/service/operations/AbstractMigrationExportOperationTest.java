package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatus;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties.Defaults;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties.Timeouts;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.ExtractorService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionExportService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractMigrationExportOperationTest {

  private static final UUID MIGRATION_ID = UUID.randomUUID();
  private static final MigrationFileType FILE_TYPE = MigrationFileType.DEBT_POSITIONS_TYPE;
  private static final String EXTRACTION_ID = "extract-123";
  private static final String EXTRACTOR_ERROR = "EXTRACTOR_ERROR";
  private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(
    2026, Month.OCTOBER, 28, 12, 0
  );
  private static final ExtractionRequest EXTRACTION_REQUEST =
    new ExtractionRequest(List.of("IPA1"), FILE_TYPE, null, null);

  @Mock
  private ExtractorService extractorServiceMock;
  @Mock
  private MigrationFileExecutionExportService migrationFileExecutionServiceMock;

  private AbstractMigrationExportOperation operation;
  @BeforeEach
  void setUp() {
    operation = operation(Duration.ofSeconds(10), Duration.ofMinutes(30), Map.of());
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(extractorServiceMock, migrationFileExecutionServiceMock);
  }

  @Test
  void givenPendingFileWhenExecuteThenSubmitsPollsAndPersistsCompletion() {
    givenFileExecution(MigrationFileExecutionStatus.PENDING, null);
    when(extractorServiceMock.createExtraction(EXTRACTION_REQUEST))
      .thenReturn(new ExtractionAcceptedResponse(EXTRACTION_ID));
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenReturn(
      newExtractionStatusResponse(EXTRACTION_ID, ExtractionStatus.RUNNING, List.of()),
      newExtractionStatusResponse(EXTRACTION_ID, ExtractionStatus.COMPLETED, List.of(
        new ArchiveFile(" archive/export-1.zip ", List.of()),
        new ArchiveFile("  ", List.of()),
        new ArchiveFile(null, List.of()),
        new ArchiveFile("archive/export-2.zip", List.of())
      ))
    );

    operation.execute(request());

    InOrder calls = inOrder(migrationFileExecutionServiceMock, extractorServiceMock);
    calls.verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
    calls.verify(migrationFileExecutionServiceMock).markExporting(
      eq(MIGRATION_ID), eq(FILE_TYPE), isA(LocalDateTime.class)
    );
    calls.verify(extractorServiceMock).createExtraction(EXTRACTION_REQUEST);
    calls.verify(migrationFileExecutionServiceMock).recordExtractionAccepted(MIGRATION_ID, FILE_TYPE, EXTRACTION_ID);
    calls.verify(extractorServiceMock, times(2)).getExtractionStatus(EXTRACTION_ID);
    calls.verify(migrationFileExecutionServiceMock).markExported(
      MIGRATION_ID,
      FILE_TYPE,
      "archive/export-1.zip,archive/export-2.zip",
      COMPLETED_AT
    );
  }

  @Test
  void givenExportedFileWhenExecuteThenDoesNotSubmitOrPoll() {
    givenFileExecution(MigrationFileExecutionStatus.EXPORTED, EXTRACTION_ID);

    operation.execute(request());

    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
  }

  @ParameterizedTest
  @MethodSource("nonRunnableFileStatuses")
  void givenNonRunnableFileStatusWhenExecuteThenRejectsExecution(MigrationFileExecutionStatus fileStatus,
                                                                 String message) {
    MigrationRequestDto request = request();

    givenFileExecution(fileStatus, EXTRACTION_ID);

    IllegalStateException failure = assertThrows(
      IllegalStateException.class,
      () -> operation.execute(request)
    );

    assertTrue(failure.getMessage().contains(message));
    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
  }

  @Test
  void givenAcceptedExtractionWhenExecuteThenResumesWithoutPostingAgain() {
    givenFileExecution(MigrationFileExecutionStatus.EXPORTING, EXTRACTION_ID);
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenReturn(
      newExtractionStatusResponse(EXTRACTION_ID, ExtractionStatus.COMPLETED, List.of())
    );

    operation.execute(request());

    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
    verify(extractorServiceMock).getExtractionStatus(EXTRACTION_ID);
    verify(migrationFileExecutionServiceMock).markExported(
      MIGRATION_ID, FILE_TYPE, "", COMPLETED_AT
    );
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void givenExportingFileWithoutAcceptedIdWhenExecuteThenFailsClosed(String extractionId) {
    MigrationRequestDto request = request();

    givenFileExecution(MigrationFileExecutionStatus.EXPORTING, extractionId);

    assertThrows(IllegalStateException.class, () -> operation.execute(request));

    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
  }

  @ParameterizedTest
  @MethodSource("invalidAcceptedResponses")
  void givenInvalidAcceptedResponseWhenExecuteThenMarksFileFailed(ExtractionAcceptedResponse accepted) {
    MigrationRequestDto request = request();

    givenFileExecution(MigrationFileExecutionStatus.PENDING, null);
    when(extractorServiceMock.createExtraction(EXTRACTION_REQUEST)).thenReturn(accepted);

    IllegalStateException failure = assertThrows(
      IllegalStateException.class,
      () -> operation.execute(request)
    );

    assertEquals("Extractor returned an invalid accepted extraction ID", failure.getMessage());
    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionServiceMock).markExporting(
      eq(MIGRATION_ID), eq(FILE_TYPE), isA(LocalDateTime.class)
    );
    verify(extractorServiceMock).createExtraction(EXTRACTION_REQUEST);
    verify(migrationFileExecutionServiceMock).markExtractionFailed(
      MIGRATION_ID, FILE_TYPE, EXTRACTOR_ERROR, failure.getMessage()
    );
  }

  @Test
  void givenSubmissionFailureWhenExecuteThenMarksFileFailedAndPropagates() {
    ResourceAccessException failure = new ResourceAccessException("connection failed");
    MigrationRequestDto request = request();

    givenFileExecution(MigrationFileExecutionStatus.PENDING, null);
    when(extractorServiceMock.createExtraction(EXTRACTION_REQUEST)).thenThrow(failure);

    assertSame(failure, assertThrows(
      ResourceAccessException.class,
      () -> operation.execute(request)
    ));

    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
    verify(migrationFileExecutionServiceMock).markExporting(
      eq(MIGRATION_ID), eq(FILE_TYPE), isA(LocalDateTime.class)
    );
    verify(extractorServiceMock).createExtraction(EXTRACTION_REQUEST);
    verify(migrationFileExecutionServiceMock).markExtractionFailed(
      MIGRATION_ID, FILE_TYPE, EXTRACTOR_ERROR, failure.getMessage()
    );
  }

  @ParameterizedTest
  @MethodSource("invalidStatuses")
  void givenInvalidExtractorStatusWhenExecuteThenMarksFileFailed(ExtractionStatusResponse response,
                                                                 Class<? extends RuntimeException> exceptionType,
                                                                 String expectedExceptionMessage,
                                                                 String expectedFailureMessage) {
    givenFileExecution(MigrationFileExecutionStatus.EXPORTING, EXTRACTION_ID);
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenReturn(response);

    RuntimeException failure = assertThrows(
      exceptionType,
      () -> operation.execute(request())
    );

    if (expectedExceptionMessage != null) {
      assertEquals(expectedExceptionMessage, failure.getMessage());
    }
    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
    verify(extractorServiceMock).getExtractionStatus(EXTRACTION_ID);
    verify(migrationFileExecutionServiceMock).markExtractionFailed(
      MIGRATION_ID,
      FILE_TYPE,
      EXTRACTOR_ERROR,
      expectedFailureMessage == null ? failure.getMessage() : expectedFailureMessage
    );
  }

  @Test
  void givenStatusReadFailureWhenExecuteThenMarksFileFailedAndPropagates() {
    ResourceAccessException failure = new ResourceAccessException("status unavailable");
    MigrationRequestDto request = request();

    givenFileExecution(MigrationFileExecutionStatus.EXPORTING, EXTRACTION_ID);
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenThrow(failure);

    assertSame(failure, assertThrows(
      ResourceAccessException.class,
      () -> operation.execute(request)
    ));

    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
    verify(extractorServiceMock).getExtractionStatus(EXTRACTION_ID);
    verify(migrationFileExecutionServiceMock).markExtractionFailed(
      MIGRATION_ID, FILE_TYPE, EXTRACTOR_ERROR, failure.getMessage()
    );
  }

  @Test
  void givenExpiredTimeoutWhenSleepUntilNextPollThenThrowsTimeout() {
    long startedAtMillis = System.currentTimeMillis() - Duration.ofMinutes(31).toMillis();

    IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
      ReflectionTestUtils.invokeMethod(operation, "sleepUntilNextPoll", startedAtMillis, EXTRACTION_ID));

    assertEquals("Timed out waiting for extraction " + EXTRACTION_ID, failure.getMessage());
  }

  @Test
  void givenFileTypeTimeoutWhenSleepUntilNextPollThenUsesOverrideInsteadOfGlobalTimeout() {
    operation = operation(Duration.ofSeconds(1100), Duration.ofHours(4), Map.of(FILE_TYPE, Duration.ofMinutes(30)));
    long startedAtMillis = System.currentTimeMillis() - Duration.ofMinutes(31).toMillis();

    IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
      ReflectionTestUtils.invokeMethod(operation, "sleepUntilNextPoll", startedAtMillis, EXTRACTION_ID));

    assertEquals("Timed out waiting for extraction " + EXTRACTION_ID, failure.getMessage());
  }

  @Test
  void givenUnexpiredGlobalTimeoutWhenSleepUntilNextPollThenUsesGlobalTimeout() {
    operation = operation(Duration.ofSeconds(1100), Duration.ofHours(4), Map.of());
    long startedAtMillis = System.currentTimeMillis() - Duration.ofMinutes(31).toMillis();

    Thread.currentThread().interrupt();
    try {
      IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
        ReflectionTestUtils.invokeMethod(operation, "sleepUntilNextPoll", startedAtMillis, EXTRACTION_ID));
      assertEquals("Interrupted while waiting for extraction " + EXTRACTION_ID, failure.getMessage());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @ParameterizedTest
  @MethodSource("nonTerminalStatuses")
  void givenInterruptedStatusWaitWhenExecuteThenRestoresInterruptAndMarksFileFailed(ExtractionStatus status) {
    MigrationRequestDto request = request();

    givenFileExecution(MigrationFileExecutionStatus.EXPORTING, EXTRACTION_ID);
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenAnswer(invocation -> {
      Thread.currentThread().interrupt();
      return newExtractionStatusResponse(EXTRACTION_ID, status, List.of());
    });

    try {
      IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> operation.execute(request)
      );

      assertEquals("Interrupted while waiting for extraction " + EXTRACTION_ID, failure.getMessage());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }

    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
    verify(extractorServiceMock).getExtractionStatus(EXTRACTION_ID);
    verify(migrationFileExecutionServiceMock).markExtractionFailed(
      MIGRATION_ID,
      FILE_TYPE,
      EXTRACTOR_ERROR,
      "Interrupted while waiting for extraction " + EXTRACTION_ID
    );
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void givenStatusFailureWithBlankMessageAndFailurePersistenceWhenExecuteThenPreservesOriginalError(String errorMessage) {
    IllegalArgumentException failure = new IllegalArgumentException(errorMessage);
    IllegalStateException persistenceFailure = new IllegalStateException("database unavailable");
    MigrationRequestDto request = request();

    givenFileExecution(MigrationFileExecutionStatus.EXPORTING, EXTRACTION_ID);
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenThrow(failure);
    doThrow(persistenceFailure).when(migrationFileExecutionServiceMock).markExtractionFailed(
      MIGRATION_ID, FILE_TYPE, EXTRACTOR_ERROR, IllegalArgumentException.class.getSimpleName()
    );

    IllegalArgumentException thrown = assertThrows(
      IllegalArgumentException.class,
      () -> operation.execute(request)
    );

    assertSame(failure, thrown);
    assertEquals(List.of(persistenceFailure), List.of(thrown.getSuppressed()));
    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
    verify(extractorServiceMock).getExtractionStatus(EXTRACTION_ID);
    verify(migrationFileExecutionServiceMock).markExtractionFailed(
      MIGRATION_ID, FILE_TYPE, EXTRACTOR_ERROR, IllegalArgumentException.class.getSimpleName()
    );
  }

  private static Stream<Arguments> nonRunnableFileStatuses() {
    return Stream.of(
      Arguments.of(MigrationFileExecutionStatus.FAILED, "already FAILED"),
      Arguments.of(MigrationFileExecutionStatus.COMPLETED, "from status COMPLETED")
    );
  }

  private static Stream<Arguments> invalidAcceptedResponses() {
    return Stream.of(
      Arguments.of((ExtractionAcceptedResponse) null),
      Arguments.of(new ExtractionAcceptedResponse(""))
    );
  }

  private static Stream<Arguments> invalidStatuses() {
    return Stream.of(
      Arguments.of(
        null,
        IllegalStateException.class,
        "Extractor returned no status for extraction " + EXTRACTION_ID,
        "Extractor returned no status for extraction " + EXTRACTION_ID
      ),
      Arguments.of(
        newExtractionStatusResponse("another-id", ExtractionStatus.COMPLETED, List.of()),
        IllegalStateException.class,
        "Extractor returned a different extraction ID for " + EXTRACTION_ID,
        "Extractor returned a different extraction ID for " + EXTRACTION_ID
      ),
      Arguments.of(
        newExtractionStatusResponse(EXTRACTION_ID, ExtractionStatus.FAILED, List.of()).error("remote failure"),
        IllegalStateException.class,
        "Extractor failed extraction " + EXTRACTION_ID + " with status FAILED: remote failure",
        "Extractor failed extraction " + EXTRACTION_ID + " with status FAILED: remote failure"
      ),
      Arguments.of(
        newExtractionStatusResponse(EXTRACTION_ID, ExtractionStatus.COMPLETED, null),
        IllegalStateException.class,
        "Extractor completed without an archive list",
        "Extractor completed without an archive list"
      ),
      Arguments.of(
        new ExtractionStatusResponse()
          .extractionId(EXTRACTION_ID)
          .status(ExtractionStatus.COMPLETED)
          .archiveFiles(List.of()),
        NullPointerException.class,
        null,
        null
      )
    );
  }

  private static Stream<ExtractionStatus> nonTerminalStatuses() {
    return Stream.of(ExtractionStatus.RUNNING, null);
  }

  private AbstractMigrationExportOperation operation(Duration interval, Duration timeout,
                                                     Map<MigrationFileType, Duration> overrides) {
    return new AbstractMigrationExportOperation(
      FILE_TYPE, extractorServiceMock, migrationFileExecutionServiceMock,
      new ExtractionPollingProperties(interval, timeout,
        new Timeouts(new Defaults(Duration.ofMinutes(30), Duration.ofHours(1)), overrides))
    ) { };
  }

  private void givenFileExecution(MigrationFileExecutionStatus status, String extractionId) {
    when(migrationFileExecutionServiceMock.ensurePending(MIGRATION_ID, FILE_TYPE))
      .thenReturn(MigrationFileExecution.builder()
        .migrationId(MIGRATION_ID)
        .fileType(FILE_TYPE)
        .status(status)
        .extractionId(extractionId)
        .build());
  }

  private MigrationRequestDto request() {
    return new MigrationRequestDto()
      .action(MigrationAction.EXPORT)
      .migrationId(MIGRATION_ID)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipaCodes(List.of("IPA1"))
      .fileTypesToInclude(List.of(FILE_TYPE));
  }

  private static ExtractionStatusResponse newExtractionStatusResponse(String extractionId,
                                                                      ExtractionStatus status,
                                                                      List<ArchiveFile> archiveFiles) {
    return new ExtractionStatusResponse()
      .extractionId(extractionId)
      .status(status)
      .updatedAt(OffsetDateTime.of(COMPLETED_AT, ZoneOffset.ofHours(2)))
      .archiveFiles(archiveFiles);
  }
}
