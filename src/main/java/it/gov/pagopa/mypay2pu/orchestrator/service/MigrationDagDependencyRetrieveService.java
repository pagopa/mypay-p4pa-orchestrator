package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationDagDependencyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationDagDependencyRetrieveService {

  private final MigrationDagDependencyRepository repository;
  private List<MigrationDagDependency> dependencies;

  public MigrationDagDependencyRetrieveService(MigrationDagDependencyRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public synchronized List<MigrationDagDependency> retrieve() {
    if (dependencies == null) {
      dependencies = List.copyOf(repository.findAll());
    }
    return dependencies;
  }
}
