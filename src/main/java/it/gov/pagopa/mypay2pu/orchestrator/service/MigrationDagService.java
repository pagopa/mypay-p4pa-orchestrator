package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationDagValidator;

import java.util.Collection;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationDagService {
  private final MigrationDagDependencyRetrieveService migrationDagDependencyRetrieveService;
  private final MigrationDagEngine dagEngine;
  private final MigrationDagValidator dagValidator;

  public MigrationDagService(MigrationDagDependencyRetrieveService migrationDagDependencyRetrieveService,
                             MigrationDagEngine dagEngine,
                             MigrationDagValidator dagValidator) {
    this.migrationDagDependencyRetrieveService = migrationDagDependencyRetrieveService;
    this.dagEngine = dagEngine;
    this.dagValidator = dagValidator;
  }

  @Transactional(readOnly = true)
  public MigrationDag findDag(Collection<MigrationFileType> fileTypesToInclude) {
    validateFileTypes(fileTypesToInclude);
    return filterDag(fileTypesToInclude);
  }

  @Transactional(readOnly = true)
  public MigrationDag findInitialDag(Collection<MigrationFileType> fileTypesToInclude) {
    validateFileTypes(fileTypesToInclude);
    MigrationDag dag = filterDag(fileTypesToInclude);
    dagValidator.validateInitialCycleDependencies(dag, fileTypesToInclude);
    return dag;
  }

  private MigrationDag filterDag(Collection<MigrationFileType> fileTypesToInclude) {
    return dagEngine.filterTree(migrationDagDependencyRetrieveService.retrieve(), fileTypesToInclude);
  }

  private void validateFileTypes(Collection<MigrationFileType> fileTypesToInclude) {
    Objects.requireNonNull(fileTypesToInclude, "fileTypesToInclude must not be null");
    if (fileTypesToInclude.isEmpty()) {
      throw new IllegalArgumentException("fileTypesToInclude must not be empty");
    }
  }
}
