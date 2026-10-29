package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatus;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.ExtractorService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MigrationExportOperationExecutor {

  private static final String EXTRACTOR_ERROR = "EXTRACTOR_ERROR";

  private final ExtractorService extractorService;
  private final MigrationFileExecutionService migrationFileExecutionService;
  private final ExtractionPollingProperties pollingProperties;

  public MigrationExportOperationExecutor(ExtractorService extractorService,
                                          MigrationFileExecutionService migrationFileExecutionService,
                                          ExtractionPollingProperties pollingProperties) {
    this.extractorService = extractorService;
    this.migrationFileExecutionService = migrationFileExecutionService;
    this.pollingProperties = pollingProperties;
  }

  public void execute(MigrationRequestDto request, MigrationFileType fileType) {
    UUID migrationId = request.getMigrationId();
    MigrationFileExecution fileExecution = migrationFileExecutionService.ensurePending(migrationId, fileType);
    String extractionId = prepareExtraction(request, fileType, fileExecution);
    if (extractionId == null) {
      return;
    }

    try {
      ExtractionStatusResponse completed = waitForCompletion(extractionId);
      migrationFileExecutionService.markExported(
        migrationId,
        fileType,
        archiveNames(completed.getArchiveFiles()),
        completed.getUpdatedAt().toLocalDateTime()
      );
    } catch (RuntimeException failure) {
      markFailed(migrationId, fileType, EXTRACTOR_ERROR, failure.getMessage(), failure);
      throw failure;
    }
  }

  private String prepareExtraction(MigrationRequestDto request,
                                   MigrationFileType fileType,
                                   MigrationFileExecution fileExecution) {
    UUID migrationId = request.getMigrationId();
    return switch (fileExecution.getStatus()) {
      case EXPORTED -> null;
      case FAILED -> throw new IllegalStateException(
        "Extraction for migration %s and file type %s is already FAILED".formatted(migrationId, fileType)
      );
      case EXPORTING -> resumeAcceptedExtraction(migrationId, fileType, fileExecution);
      case PENDING -> submitExtraction(request, fileType);
      default -> throw new IllegalStateException(
        "Cannot export migration %s and file type %s from status %s"
          .formatted(migrationId, fileType, fileExecution.getStatus())
      );
    };
  }

  private String resumeAcceptedExtraction(UUID migrationId,
                                          MigrationFileType fileType,
                                          MigrationFileExecution fileExecution) {
    String extractionId = fileExecution.getExtractionId();
    if (extractionId == null || extractionId.isBlank()) {
      throw new IllegalStateException(
        "Cannot resume extraction for migration %s and file type %s: EXPORTING has no accepted extraction ID"
          .formatted(migrationId, fileType)
      );
    }
    return extractionId;
  }

  private String submitExtraction(MigrationRequestDto request, MigrationFileType fileType) {
    UUID migrationId = request.getMigrationId();
    migrationFileExecutionService.markExporting(migrationId, fileType, LocalDateTime.now(ZoneOffset.UTC));
    try {
      ExtractionAcceptedResponse accepted = extractorService.createExtraction(
        new ExtractionRequest(List.copyOf(request.getIpaCodes()), fileType, null, null)
      );
      if (accepted == null || accepted.getExtractionId().isBlank()) {
        throw new IllegalStateException("Extractor returned an invalid accepted extraction ID");
      }
      migrationFileExecutionService.recordAccepted(migrationId, fileType, accepted.getExtractionId());
      return accepted.getExtractionId();
    } catch (RuntimeException failure) {
      markFailed(migrationId, fileType, EXTRACTOR_ERROR, failure.getMessage(), failure);
      throw failure;
    }
  }

  private ExtractionStatusResponse waitForCompletion(String extractionId) {
    long deadline = System.nanoTime() + pollingProperties.statusTimeout().toNanos();
    while (true) {
      ExtractionStatusResponse response = extractorService.getExtractionStatus(extractionId);
      if (response == null) {
        throw new IllegalStateException("Extractor returned no status for extraction " + extractionId);
      }
      if (!extractionId.equals(response.getExtractionId())) {
        throw new IllegalStateException("Extractor returned a different extraction ID for " + extractionId);
      }
      if (response.getStatus() == ExtractionStatus.COMPLETED) {
        return response;
      }
      if (response.getStatus() == ExtractionStatus.FAILED) {
        throw new IllegalStateException(
          "Extractor failed extraction %s with status %s: %s"
            .formatted(extractionId, response.getStatus(), response.getError())
        );
      }
      sleepUntilNextPoll(deadline, extractionId);
    }
  }

  private void sleepUntilNextPoll(long deadline, String extractionId) {
    long remainingNanos = deadline - System.nanoTime();
    if (remainingNanos <= 0) {
      throw new IllegalStateException("Timed out waiting for extraction " + extractionId);
    }
    try {
      TimeUnit.NANOSECONDS.sleep(Math.min(pollingProperties.statusPollInterval().toNanos(), remainingNanos));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for extraction " + extractionId, interrupted);
    }
  }
  private String archiveNames(List<ArchiveFile> archiveFiles) {
    if (archiveFiles == null) {
      throw new IllegalStateException("Extractor completed without an archive list");
    }
    return archiveFiles.stream()
      .map(ArchiveFile::getName)
      .filter(Objects::nonNull)
      .map(String::trim)
      .filter(name -> !name.isEmpty())
      .collect(Collectors.joining(","));
  }

  private void markFailed(UUID migrationId,
                          MigrationFileType fileType,
                          String errorCode,
                          String errorMessage,
                          RuntimeException originalFailure) {
    try {
      migrationFileExecutionService.markFailed(
        migrationId,
        fileType,
        errorCode,
        errorMessage == null || errorMessage.isBlank() ? originalFailure.getClass().getSimpleName() : errorMessage
      );
    } catch (RuntimeException persistenceFailure) {
      originalFailure.addSuppressed(persistenceFailure);
    }
  }
}
