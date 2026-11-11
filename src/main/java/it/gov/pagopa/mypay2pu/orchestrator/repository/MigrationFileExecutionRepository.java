package it.gov.pagopa.mypay2pu.orchestrator.repository;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MigrationFileExecutionRepository extends JpaRepository<MigrationFileExecution, UUID> {
  List<MigrationFileExecution> findAllByMigrationIdOrderByCreationDateAscIdAsc(UUID migrationId);

  Optional<MigrationFileExecution> findByMigrationIdAndFileType(UUID migrationId, MigrationFileType fileType);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("""
    update MigrationFileExecution fileExecution
    set fileExecution.status = :transferringStatus,
        fileExecution.uploadStartedAt = :startedAt
    where fileExecution.migrationId = :migrationId
      and fileExecution.fileType = :fileType
      and fileExecution.status = :expectedStatus
      and fileExecution.uploadId is null
    """)
  int claimTransfer(
    @Param("migrationId") UUID migrationId,
    @Param("fileType") MigrationFileType fileType,
    @Param("expectedStatus") MigrationFileExecutionStatus expectedStatus,
    @Param("transferringStatus") MigrationFileExecutionStatus transferringStatus,
    @Param("startedAt") LocalDateTime startedAt
  );
}
