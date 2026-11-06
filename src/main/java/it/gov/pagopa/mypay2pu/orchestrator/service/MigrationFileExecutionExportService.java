package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionExportValidator;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Persists validated Export updates without modifying upload data.
 */
@Service
public class MigrationFileExecutionExportService extends MigrationFileExecutionService {

  private final MigrationFileExecutionExportValidator validator;

  public MigrationFileExecutionExportService(MigrationFileExecutionRepository migrationFileExecutionRepository,
                                             MigrationFileExecutionExportValidator validator) {
    super(migrationFileExecutionRepository);
    this.validator = validator;
  }

  /**
   * Starts export once and records the extraction start time.
   */
  public MigrationFileExecution markExporting(UUID migrationId, MigrationFileType fileType, LocalDateTime startedAt) {
    return update(migrationId, fileType, fileExecution -> {
      validator.validateMarkExporting(fileExecution);
      fileExecution.setStatus(MigrationFileExecutionStatus.EXPORTING);
      fileExecution.setExtractionStartedAt(startedAt);
    });
  }

  /**
   * Records the accepted Extractor job ID without treating acceptance as completion.
   */
  public MigrationFileExecution recordExtractionAccepted(UUID migrationId, MigrationFileType fileType, String extractionId) {
    validator.validateAcceptedExtractionId(extractionId);
    return update(migrationId, fileType, fileExecution -> {
      validator.validateRecordExtractionAccepted(fileExecution, extractionId);
      fileExecution.setExtractionId(extractionId);
    });
  }

  /**
   * Records completion; identical notifications may be repeated, but do not start a new export.
   */
  public MigrationFileExecution markExported(UUID migrationId,
                                            MigrationFileType fileType,
                                            String filePath,
                                            LocalDateTime completedAt) {
    return update(migrationId, fileType, fileExecution -> {
      validator.validateMarkExported(fileExecution, filePath, completedAt);
      fileExecution.setStatus(MigrationFileExecutionStatus.EXPORTED);
      fileExecution.setFilePath(filePath);
      fileExecution.setExtractionCompletedAt(completedAt);
    });
  }

  /**
   * Records extraction failure details, not Transfer or PU errors.
   */
  public MigrationFileExecution markExtractionFailed(UUID migrationId,
                                                    MigrationFileType fileType,
                                                    String errorCode,
                                                    String errorMessage) {
    return update(migrationId, fileType, fileExecution -> {
      validator.validateMarkExtractionFailed(fileExecution, errorCode, errorMessage);
      fileExecution.setStatus(MigrationFileExecutionStatus.FAILED);
      fileExecution.setExtractionErrorCode(errorCode);
      fileExecution.setExtractionErrorMsg(errorMessage);
    });
  }
}
