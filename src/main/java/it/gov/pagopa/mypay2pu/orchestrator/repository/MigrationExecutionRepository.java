package it.gov.pagopa.mypay2pu.orchestrator.repository;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationExecutionRepository extends JpaRepository<MigrationExecution, UUID> {
}
