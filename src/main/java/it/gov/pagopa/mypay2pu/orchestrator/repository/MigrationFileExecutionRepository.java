package it.gov.pagopa.mypay2pu.orchestrator.repository;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationFileExecutionRepository extends JpaRepository<MigrationFileExecution, UUID> {
  List<MigrationFileExecution> findAllByMigrationIdOrderByCreationDateAscIdAsc(UUID migrationId);

  Optional<MigrationFileExecution> findByMigrationIdAndFileType(UUID migrationId, MigrationFileType fileType);
}
