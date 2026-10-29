package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class MigrationFileExecutionService {

  private final MigrationFileExecutionRepository migrationFileExecutionRepository;

  public MigrationFileExecutionService(MigrationFileExecutionRepository migrationFileExecutionRepository) {
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
  }

  public MigrationFileExecution ensurePending(UUID migrationId, MigrationFileType fileType) {
    return migrationFileExecutionRepository.findByMigrationIdAndFileType(migrationId, fileType)
      .orElseGet(() -> migrationFileExecutionRepository.save(MigrationFileExecution.builder()
        .migrationId(migrationId)
        .fileType(fileType)
        .status(MigrationFileExecutionStatus.PENDING)
        .build()));
  }

  public MigrationFileExecution markExporting(UUID migrationId,
                                              MigrationFileType fileType,
                                              LocalDateTime startedAt) {
    return update(migrationId, fileType, fileExecution -> {
      requireStatus(fileExecution, MigrationFileExecutionStatus.PENDING);
      if (fileExecution.getExtractionId() != null) {
        throw invalidTransition(fileExecution, "start a new extraction with an existing extraction ID");
      }
      fileExecution.setStatus(MigrationFileExecutionStatus.EXPORTING);
      fileExecution.setExtractionStartedAt(startedAt);
    });
  }

  public MigrationFileExecution recordAccepted(UUID migrationId,
                                               MigrationFileType fileType,
                                               String extractionId) {
    if (extractionId == null || extractionId.isBlank()) {
      throw new IllegalArgumentException("extractionId must not be blank");
    }
    return update(migrationId, fileType, fileExecution -> {
      requireStatus(fileExecution, MigrationFileExecutionStatus.EXPORTING);
      if (fileExecution.getExtractionId() != null) {
        if (!fileExecution.getExtractionId().equals(extractionId)) {
          throw invalidTransition(fileExecution, "replace the accepted extraction ID");
        }
        return;
      }
      fileExecution.setExtractionId(extractionId);
    });
  }

  public MigrationFileExecution markExported(UUID migrationId,
                                             MigrationFileType fileType,
                                             String filePath,
                                             LocalDateTime completedAt) {
    return update(migrationId, fileType, fileExecution -> {
      if (fileExecution.getStatus() == MigrationFileExecutionStatus.EXPORTED
        && Objects.equals(fileExecution.getFilePath(), filePath)
        && Objects.equals(fileExecution.getExtractionCompletedAt(), completedAt)) {
        return;
      }
      requireStatus(fileExecution, MigrationFileExecutionStatus.EXPORTING);
      if (fileExecution.getExtractionId() == null || fileExecution.getExtractionId().isBlank()) {
        throw invalidTransition(fileExecution, "complete extraction before an accepted ID is persisted");
      }
      fileExecution.setStatus(MigrationFileExecutionStatus.EXPORTED);
      fileExecution.setFilePath(filePath);
      fileExecution.setExtractionCompletedAt(completedAt);
    });
  }

  public MigrationFileExecution markFailed(UUID migrationId,
                                           MigrationFileType fileType,
                                           String errorCode,
                                           String errorMessage) {
    return update(migrationId, fileType, fileExecution -> {
      if (fileExecution.getStatus() == MigrationFileExecutionStatus.FAILED
        && Objects.equals(fileExecution.getExtractionErrorCode(), errorCode)
        && Objects.equals(fileExecution.getExtractionErrorMsg(), errorMessage)) {
        return;
      }
      if (fileExecution.getStatus() != MigrationFileExecutionStatus.PENDING
        && fileExecution.getStatus() != MigrationFileExecutionStatus.EXPORTING) {
        throw invalidTransition(fileExecution, "mark extraction failed");
      }
      fileExecution.setStatus(MigrationFileExecutionStatus.FAILED);
      fileExecution.setExtractionErrorCode(errorCode);
      fileExecution.setExtractionErrorMsg(errorMessage);
    });
  }

  private MigrationFileExecution update(UUID migrationId,
                                        MigrationFileType fileType,
                                        Consumer<MigrationFileExecution> transition) {
    MigrationFileExecution fileExecution = migrationFileExecutionRepository
      .findByMigrationIdAndFileType(migrationId, fileType)
      .orElseThrow(() -> new IllegalStateException(
        "File execution does not exist for migration %s and file type %s".formatted(migrationId, fileType)
      ));
    transition.accept(fileExecution);
    return migrationFileExecutionRepository.save(fileExecution);
  }

  private void requireStatus(MigrationFileExecution fileExecution, MigrationFileExecutionStatus expectedStatus) {
    if (fileExecution.getStatus() != expectedStatus) {
      throw invalidTransition(
        fileExecution,
        "transition from %s to %s".formatted(fileExecution.getStatus(), expectedStatus)
      );
    }
  }

  private IllegalStateException invalidTransition(MigrationFileExecution fileExecution, String operation) {
    return new IllegalStateException(
      "Cannot %s for file execution %s in status %s".formatted(
        operation,
        fileExecution.getId(),
        fileExecution.getStatus()
      )
    );
  }
}
