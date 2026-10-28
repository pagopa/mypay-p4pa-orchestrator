package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatus;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.ExtractorService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationOperation;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isA;

@ExtendWith(MockitoExtension.class)
class MigrationExportOperationExecutorTest {

  private static final UUID MIGRATION_ID = UUID.randomUUID();
  private static final MigrationFileType FILE_TYPE = MigrationFileType.DEBT_POSITIONS_TYPE;
  private static final String EXTRACTION_ID = "extract-123";
  private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.of(
    2026, 10, 28, 12, 0, 0, 0, ZoneOffset.ofHours(2)
  );

  @Mock
  private ExtractorService extractorServiceMock;
  @Mock
  private MigrationFileExecutionService migrationFileExecutionServiceMock;

  private MigrationExportOperationExecutor operationExecutor;

  @BeforeEach
  void setUp() {
    operationExecutor = new MigrationExportOperationExecutor(
      extractorServiceMock,
      migrationFileExecutionServiceMock,
      new ExtractionPollingProperties(Duration.ofMillis(1), Duration.ofSeconds(5))
    );
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(extractorServiceMock, migrationFileExecutionServiceMock);
  }

  @Test
  void givenPendingFileWhenExecuteThenPersistsOnlyConfirmedCompletion() {
    MigrationFileExecution pending = fileExecution(MigrationFileExecutionStatus.PENDING, null);
    when(migrationFileExecutionServiceMock.ensurePending(MIGRATION_ID, FILE_TYPE)).thenReturn(pending);
    when(extractorServiceMock.createExtraction(new ExtractionRequest(
      List.of("IPA1"), FILE_TYPE, null, null
    ))).thenReturn(new ExtractionAcceptedResponse(EXTRACTION_ID));
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenReturn(
      status(EXTRACTION_ID, ExtractionStatus.COMPLETED, List.of(new ArchiveFile("archive/export.zip", List.of())))
    );
    List<String> sequence = new ArrayList<>();
    doAnswer(invocation -> {
      sequence.add("exporting");
      return null;
    }).when(migrationFileExecutionServiceMock).markExporting(
      eq(MIGRATION_ID),
      eq(FILE_TYPE),
      isA(java.time.LocalDateTime.class)
    );
    doAnswer(invocation -> {
      sequence.add("accepted");
      return null;
    }).when(migrationFileExecutionServiceMock).recordAccepted(MIGRATION_ID, FILE_TYPE, EXTRACTION_ID);
    doAnswer(invocation -> {
      sequence.add("exported");
      return null;
    }).when(migrationFileExecutionServiceMock).markExported(
      MIGRATION_ID,
      FILE_TYPE,
      "archive/export.zip",
      COMPLETED_AT.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
    );

    operationExecutor.execute(request(FILE_TYPE), FILE_TYPE);

    assertEquals(List.of("exporting", "accepted", "exported"), sequence);
  }

  @Test
  void givenAcceptedExtractionWhenExecuteThenResumesStatusWithoutPostingAgain() {
    when(migrationFileExecutionServiceMock.ensurePending(MIGRATION_ID, FILE_TYPE))
      .thenReturn(fileExecution(MigrationFileExecutionStatus.EXPORTING, EXTRACTION_ID));
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenReturn(
      status(EXTRACTION_ID, ExtractionStatus.COMPLETED, List.of())
    );

    operationExecutor.execute(request(FILE_TYPE), FILE_TYPE);

    verify(migrationFileExecutionServiceMock).ensurePending(MIGRATION_ID, FILE_TYPE);
    verify(extractorServiceMock).getExtractionStatus(EXTRACTION_ID);
    verify(migrationFileExecutionServiceMock).markExported(
      MIGRATION_ID,
      FILE_TYPE,
      "",
      COMPLETED_AT.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
    );
  }

  @Test
  void givenExportingFileWithoutAcceptedIdWhenExecuteThenFailsClosed() {
    when(migrationFileExecutionServiceMock.ensurePending(MIGRATION_ID, FILE_TYPE))
      .thenReturn(fileExecution(MigrationFileExecutionStatus.EXPORTING, null));

    MigrationRequestDto request = request(FILE_TYPE);
    assertThrows(IllegalStateException.class, () -> operationExecutor.execute(request, FILE_TYPE));
  }

  @Test
  void givenPostFailureWhenExecuteThenMarksFailedWithoutRetryingSubmission() {
    ResourceAccessException failure = new ResourceAccessException("connection failed", new IOException("offline"));
    when(migrationFileExecutionServiceMock.ensurePending(MIGRATION_ID, FILE_TYPE))
      .thenReturn(fileExecution(MigrationFileExecutionStatus.PENDING, null));
    when(extractorServiceMock.createExtraction(new ExtractionRequest(List.of("IPA1"), FILE_TYPE, null, null)))
      .thenThrow(failure);

    MigrationRequestDto request = request(FILE_TYPE);
    assertThrows(ResourceAccessException.class, () -> operationExecutor.execute(request, FILE_TYPE));
  }

  @Test
  void givenStatusErrorWhenExecuteThenMarksFailed() {
    HttpClientErrorException failure = new HttpClientErrorException(HttpStatus.BAD_REQUEST);
    when(migrationFileExecutionServiceMock.ensurePending(MIGRATION_ID, FILE_TYPE))
      .thenReturn(fileExecution(MigrationFileExecutionStatus.EXPORTING, EXTRACTION_ID));
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenThrow(failure);

    MigrationRequestDto request = request(FILE_TYPE);
    assertThrows(HttpClientErrorException.class, () -> operationExecutor.execute(request, FILE_TYPE));
  }

  @Test
  void givenRunningStatusUntilTimeoutWhenExecuteThenMarksFailedWithoutMarkingExported() {
    when(migrationFileExecutionServiceMock.ensurePending(MIGRATION_ID, FILE_TYPE))
      .thenReturn(fileExecution(MigrationFileExecutionStatus.EXPORTING, EXTRACTION_ID));
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenReturn(
      status(EXTRACTION_ID, ExtractionStatus.RUNNING, List.of())
    );
    operationExecutor = new MigrationExportOperationExecutor(
      extractorServiceMock,
      migrationFileExecutionServiceMock,
      new ExtractionPollingProperties(Duration.ofMillis(1), Duration.ofMillis(5))
    );

    MigrationRequestDto request = request(FILE_TYPE);
    assertThrows(IllegalStateException.class, () -> operationExecutor.execute(request, FILE_TYPE));
  }

  @Test
  void givenFailedSelectedParentWhenExecutePlanThenDoesNotStartSelectedDescendant() {
    when(migrationFileExecutionServiceMock.ensurePending(MIGRATION_ID, FILE_TYPE))
      .thenReturn(fileExecution(MigrationFileExecutionStatus.EXPORTING, EXTRACTION_ID));
    when(extractorServiceMock.getExtractionStatus(EXTRACTION_ID)).thenReturn(
      status(EXTRACTION_ID, ExtractionStatus.FAILED, List.of()).error("remote failure")
    );
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
    Map<MigrationFileType, MigrationOperation> operations = new EnumMap<>(MigrationFileType.class);
    operations.put(FILE_TYPE, operation(FILE_TYPE, executed, true));
    operations.put(MigrationFileType.ORGANIZATIONS, operation(MigrationFileType.ORGANIZATIONS, executed, false));
    MigrationRequestDto request = request(FILE_TYPE, MigrationFileType.ORGANIZATIONS);

    MigrationPlanExecutorService migrationPlanExecutorService = new MigrationPlanExecutorService();
    assertThrows(
      IllegalStateException.class,
      () -> migrationPlanExecutorService.execute(request, operations)
    );

    assertEquals(Set.of(FILE_TYPE), executed);
  }

  private MigrationOperation operation(
    MigrationFileType fileType,
    Set<MigrationFileType> executed,
    boolean executeExtractor
  ) {
    return new MigrationOperation() {
      @Override
      public MigrationFileType getMigrationFileType() {
        return fileType;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        executed.add(fileType);
        if (executeExtractor) {
          operationExecutor.execute(request, fileType);
        }
      }
    };
  }

  private MigrationRequestDto request(MigrationFileType... selectedFileTypes) {
    return new MigrationRequestDto()
      .action(MigrationAction.EXPORT)
      .migrationId(MIGRATION_ID)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipaCodes(List.of("IPA1"))
      .fileTypesToInclude(List.of(selectedFileTypes));
  }

  private MigrationFileExecution fileExecution(MigrationFileExecutionStatus status, String extractionId) {
    return MigrationFileExecution.builder()
      .id(UUID.randomUUID())
      .migrationId(MIGRATION_ID)
      .fileType(FILE_TYPE)
      .status(status)
      .extractionId(extractionId)
      .build();
  }

  private ExtractionStatusResponse status(
    String extractionId,
    ExtractionStatus status,
    List<ArchiveFile> archiveFiles
  ) {
    return new ExtractionStatusResponse()
      .extractionId(extractionId)
      .status(status)
      .updatedAt(COMPLETED_AT)
      .archiveFiles(archiveFiles);
  }
}
