package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ArchiveFile;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionAcceptedResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatus;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionStatusResponse;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.ExtractorService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionExportService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Executes the shared extraction lifecycle for a single migration file type.
 *
 * <p>Subclasses configure their file type and inherit the submission, polling and persistence
 * behavior. An accepted extraction can be resumed using its persisted ID; an already exported
 * file is not submitted again. Failures are propagated so dependent operations do not proceed.
 */
@Slf4j
public abstract class AbstractMigrationExportOperation implements MigrationExportOperation {

  private static final String EXTRACTOR_ERROR = "EXTRACTOR_ERROR";

  private final MigrationFileType fileType;
  private final ExtractorService extractorService;
  private final MigrationFileExecutionExportService migrationFileExecutionExportService;
  private final ExtractionPollingProperties pollingProperties;

  /**
   * Configures the file type and services used by this export operation.
   *
   * @param fileType file type handled by the operation
   * @param extractorService client for submitting and checking extractions
   * @param migrationFileExecutionExportService service persisting export progress
   * @param pollingProperties polling interval and timeout
   * @throws NullPointerException if {@code fileType} is null
   */
  protected AbstractMigrationExportOperation(MigrationFileType fileType,
                                             ExtractorService extractorService,
                                             MigrationFileExecutionExportService migrationFileExecutionExportService,
                                             ExtractionPollingProperties pollingProperties) {
    this.fileType = Objects.requireNonNull(fileType, "fileType");
    this.extractorService = extractorService;
    this.migrationFileExecutionExportService = migrationFileExecutionExportService;
    this.pollingProperties = pollingProperties;
  }

  /**
   * Returns the file type configured for this operation.
   *
   * @return the migration file type
   */
  @Override
  public final MigrationFileType getMigrationFileType() {
    return fileType;
  }

  /**
   * Starts or resumes extraction and persists its outcome.
   *
   * <p>A pending file is submitted to Extractor, while an exporting file resumes polling its
   * accepted extraction ID. An exported file is left unchanged. Completion records the archive
   * names and extraction completion time; submission and polling failures are persisted when
   * possible and then propagated.
   *
   * @param request migration request containing the execution ID and IPA codes
   * @throws IllegalStateException if extraction cannot proceed or does not complete successfully
   */
  @Override
  public final void execute(MigrationRequestDto request) {
    UUID migrationId = request.getMigrationId();
    MigrationFileExecution fileExecution = migrationFileExecutionExportService.ensurePending(migrationId, fileType);
    String extractionId = prepareExtraction(request, fileExecution);
    if (extractionId == null) {
      return;
    }

    try {
      ExtractionStatusResponse completed = waitForCompletion(migrationId, extractionId);
      migrationFileExecutionExportService.markExported(
        migrationId,
        fileType,
        archiveNames(completed.getArchiveFiles()),
        completed.getUpdatedAt().toLocalDateTime()
      );
    } catch (RuntimeException failure) {
      markFailed(migrationId, EXTRACTOR_ERROR, failure.getMessage(), failure);
      throw failure;
    }
  }

  /**
   * Selects the next action according to the persisted file status.
   *
   * @param request migration request used for a new extraction
   * @param fileExecution current persisted file execution
   * @return the accepted extraction ID, or {@code null} if already exported
   * @throws IllegalStateException if the file status does not allow export
   */
  private String prepareExtraction(MigrationRequestDto request, MigrationFileExecution fileExecution) {
    UUID migrationId = request.getMigrationId();
    return switch (fileExecution.getStatus()) {
      case EXPORTED -> null;
      case FAILED -> throw new IllegalStateException(
        "Extraction for migration %s and file type %s is already FAILED".formatted(migrationId, fileType)
      );
      case EXPORTING -> resumeAcceptedExtraction(migrationId, fileExecution);
      case PENDING -> submitExtraction(request);
      default -> throw new IllegalStateException(
        "Cannot export migration %s and file type %s from status %s"
          .formatted(migrationId, fileType, fileExecution.getStatus())
      );
    };
  }

  /**
   * Retrieves the persisted job ID needed to resume polling without submitting another request.
   *
   * @param migrationId migration identifier used in error messages
   * @param fileExecution exporting file execution
   * @return the accepted extraction ID
   * @throws IllegalStateException if the extraction ID is missing or blank
   */
  private String resumeAcceptedExtraction(UUID migrationId, MigrationFileExecution fileExecution) {
    String extractionId = fileExecution.getExtractionId();
    if (extractionId == null || extractionId.isBlank()) {
      throw new IllegalStateException(
        "Cannot resume extraction for migration %s and file type %s: EXPORTING has no accepted extraction ID"
          .formatted(migrationId, fileType)
      );
    }
    return extractionId;
  }

  /**
   * Marks the file as exporting and submits a new extraction to Extractor.
   *
   * <p>The accepted job ID is persisted before polling. Submission and acceptance failures are
   * recorded against the file execution when possible and propagated to the caller.
   *
   * @param request migration request containing the migration ID and IPA codes
   * @return the accepted extraction ID
   */
  private String submitExtraction(MigrationRequestDto request) {
    UUID migrationId = request.getMigrationId();
    migrationFileExecutionExportService.markExporting(migrationId, fileType, LocalDateTime.now(ZoneOffset.UTC));
    try {
      ExtractionAcceptedResponse accepted = extractorService.createExtraction(
        new ExtractionRequest(List.copyOf(request.getIpaCodes()), fileType, null, null)
      );
      if (accepted == null || !StringUtils.hasText(accepted.getExtractionId())) {
        throw new IllegalStateException("Extractor returned an invalid accepted extraction ID");
      }
      migrationFileExecutionExportService.recordExtractionAccepted(migrationId, fileType, accepted.getExtractionId());
      return accepted.getExtractionId();
    } catch (RuntimeException failure) {
      markFailed(migrationId, EXTRACTOR_ERROR, failure.getMessage(), failure);
      throw failure;
    }
  }

  /**
   * Polls Extractor until the accepted extraction completes or fails.
   *
   * <p>The timeout is checked between status requests using the system clock;
   * it does not interrupt an HTTP request already in progress.
   *
   * <p>Only initial {@code EXPORT_FILE_NOT_FOUND} responses are retried. Once a valid status
   * has been observed, subsequent 404 responses are propagated immediately.
   *
   * @param migrationId migration identifier used in retry logs
   * @param extractionId accepted Extractor job ID
   * @return the completed extraction response
   * @throws IllegalStateException if a response is invalid, extraction fails, or polling times out
   */
  private ExtractionStatusResponse waitForCompletion(UUID migrationId, String extractionId) {
    long startedAtMillis = System.currentTimeMillis();
    ExtractionStatusResponse response = waitForInitialStatus(migrationId, extractionId, startedAtMillis);
    while (true) {
      if (response.getStatus() == ExtractionStatus.COMPLETED) {
        return response;
      }
      if (response.getStatus() == ExtractionStatus.FAILED) {
        throw new IllegalStateException(
          "Extractor failed extraction %s with status %s: %s"
            .formatted(extractionId, response.getStatus(), response.getError())
        );
      }
      sleepUntilNextPoll(startedAtMillis, extractionId);
      response = readStatus(extractionId);
    }
  }

  /**
   * Retrieves the first valid status, retrying only 404 responses with code {@code EXPORT_FILE_NOT_FOUND}.
   *
   * <p>The configured attempt limit includes the initial request. Retries share the overall
   * polling timeout and interval. If waiting fails, the last 404 is attached as a suppressed exception.
   *
   * @param migrationId migration identifier used in retry logs
   * @param extractionId accepted Extractor job ID
   * @param startedAtMillis polling start time from {@link System#currentTimeMillis()}
   * @return the first status response validated by {@link #readStatus(String)}
   * @throws NotFoundException if the attempt limit is reached or the error is not retryable
   * @throws IllegalStateException if the response is invalid, polling times out, or waiting is interrupted
   */
  private ExtractionStatusResponse waitForInitialStatus(UUID migrationId, String extractionId, long startedAtMillis) {
    int maxAttempts = pollingProperties.statusNotFoundMaxAttempts();
    for (int attempt = 1; ; attempt++) {
      try {
        return readStatus(extractionId);
      } catch (NotFoundException failure) {
        if (attempt >= maxAttempts || !"EXPORT_FILE_NOT_FOUND".equals(failure.getCode())) {
          throw failure;
        }
        log.warn("Extractor status not yet visible: migrationId={} fileType={} extractionId={} attempt={}/{}",
          migrationId, fileType, extractionId, attempt, maxAttempts);
        try {
          sleepUntilNextPoll(startedAtMillis, extractionId);
        } catch (IllegalStateException waitFailure) {
          waitFailure.addSuppressed(failure);
          throw waitFailure;
        }
      }
    }
  }

  /**
   * Fetches an extraction status and verifies that it belongs to the requested extraction.
   *
   * @param extractionId accepted Extractor job ID
   * @return the non-null response matching the requested extraction ID
   * @throws IllegalStateException if the response is null or contains a different extraction ID
   */
  private ExtractionStatusResponse readStatus(String extractionId) {
    ExtractionStatusResponse response = extractorService.getExtractionStatus(extractionId);
    if (response == null) {
      throw new IllegalStateException("Extractor returned no status for extraction " + extractionId);
    }
    if (!extractionId.equals(response.getExtractionId())) {
      throw new IllegalStateException("Extractor returned a different extraction ID for " + extractionId);
    }
    return response;
  }

  /**
   * Waits for the configured interval, bounded by the remaining polling timeout.
   *
   * @param startedAtMillis polling start time from {@link System#currentTimeMillis()}
   * @param extractionId extraction ID used in error messages
   * @throws IllegalStateException if the timeout expires or the wait is interrupted
   */
  private void sleepUntilNextPoll(long startedAtMillis, String extractionId) {
    long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startedAtMillis);
    long remainingSeconds = pollingProperties.resolveFileTypeConfigurationStatusTimeout(fileType).toSeconds() - elapsedSeconds;
    if (remainingSeconds <= 0) {
      throw new IllegalStateException("Timed out waiting for extraction " + extractionId);
    }
    try {
      TimeUnit.SECONDS.sleep(Math.min(pollingProperties.statusPollInterval().toSeconds(), remainingSeconds));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for extraction " + extractionId, interrupted);
    }
    if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startedAtMillis)
        >= pollingProperties.resolveFileTypeConfigurationStatusTimeout(fileType).toSeconds()) {
      throw new IllegalStateException("Timed out waiting for extraction " + extractionId);
    }
  }

  /**
   * Collects nonblank archive names into the comma-separated value stored with the execution.
   *
   * @param archiveFiles archives returned by a completed extraction
   * @return trimmed archive names in their original order, separated by commas
   * @throws IllegalStateException if the archive list is missing
   */
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

  /**
   * Attempts to persist extraction failure details without replacing the original error.
   *
   * <p>If persistence fails, the persistence exception is added as a suppressed exception to
   * the original failure, which the caller remains responsible for propagating.
   *
   * @param migrationId migration identifier
   * @param errorCode extraction error code to persist
   * @param errorMessage failure message, or blank to use the exception class name
   * @param originalFailure exception to which a persistence failure is attached
   */
  private void markFailed(UUID migrationId,
                          String errorCode,
                          String errorMessage,
                          RuntimeException originalFailure) {
    try {
      migrationFileExecutionExportService.markExtractionFailed(
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
