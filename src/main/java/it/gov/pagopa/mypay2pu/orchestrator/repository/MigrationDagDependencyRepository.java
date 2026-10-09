package it.gov.pagopa.mypay2pu.orchestrator.repository;

import java.util.List;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import org.springframework.data.repository.Repository;

public interface MigrationDagDependencyRepository extends Repository<MigrationDagDependency, Integer> {
  List<MigrationDagDependency> findAll();
}
