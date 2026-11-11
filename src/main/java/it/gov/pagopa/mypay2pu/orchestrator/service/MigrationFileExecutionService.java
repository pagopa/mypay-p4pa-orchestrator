package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shares file execution lookup, creation and persistence across phase-specific services.
 */
public abstract class MigrationFileExecutionService {

  private final MigrationFileExecutionRepository migrationFileExecutionRepository;

  protected MigrationFileExecutionService(MigrationFileExecutionRepository migrationFileExecutionRepository) {
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
  }

  /**
   * Ensures a file execution exists for the parent migration and file type.
   * The migration ID does not imply that this file execution has already been created.
   * Existing executions are returned without resetting their status.
   */
  public MigrationFileExecution ensurePending(UUID migrationId, MigrationFileType fileType) {
    return migrationFileExecutionRepository.findByMigrationIdAndFileType(migrationId, fileType)
      .orElseGet(() -> migrationFileExecutionRepository.save(MigrationFileExecution.builder()
        .migrationId(migrationId)
        .fileType(fileType)
        .status(MigrationFileExecutionStatus.PENDING)
        .build()));
  }

  /**
   * Loads the current record and saves only after the supplied validated update succeeds.
   */
  protected final MigrationFileExecution update(UUID migrationId,
                                                MigrationFileType fileType,
                                                Consumer<MigrationFileExecution> transition) {
    MigrationFileExecution fileExecution = findRequired(migrationId, fileType);
    transition.accept(fileExecution);
    return migrationFileExecutionRepository.save(fileExecution);
  }

  protected final MigrationFileExecution findRequired(UUID migrationId, MigrationFileType fileType) {
    return migrationFileExecutionRepository.findByMigrationIdAndFileType(migrationId, fileType)
      .orElseThrow(() -> new NotFoundException(
        "MIGRATION_FILE_EXECUTION_NOT_FOUND",
        "File execution does not exist for migration %s and file type %s".formatted(migrationId, fileType)
      ));
  }
}
