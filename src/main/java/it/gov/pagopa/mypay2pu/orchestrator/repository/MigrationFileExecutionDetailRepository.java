package it.gov.pagopa.mypay2pu.orchestrator.repository;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationFileExecutionDetailRepository extends JpaRepository<MigrationFileExecutionDetail, UUID> {
}
