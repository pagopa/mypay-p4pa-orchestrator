package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationDagDependencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationDagDependencyRetrieveService {

  private final MigrationDagDependencyRepository repository;
  private final MigrationDagEngine dagEngine;
  private MigrationDag dag;

  public MigrationDagDependencyRetrieveService(MigrationDagDependencyRepository repository,
                                               MigrationDagEngine dagEngine) {
    this.repository = repository;
    this.dagEngine = dagEngine;
  }

  @Transactional(readOnly = true)
  public synchronized MigrationDag retrieve() {
    if (dag == null) {
      dag = dagEngine.buildHierarchyTree(repository.findAll());
    }
    return dag;
  }
}
