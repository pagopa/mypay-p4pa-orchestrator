package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;

import java.util.Collection;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationDagService {
  private final MigrationDagDependencyRetrieveService migrationDagDependencyRetrieveService;
  private final MigrationDagEngine dagEngine;

  public MigrationDagService(MigrationDagDependencyRetrieveService migrationDagDependencyRetrieveService,
                             MigrationDagEngine dagEngine) {
    this.migrationDagDependencyRetrieveService = migrationDagDependencyRetrieveService;
    this.dagEngine = dagEngine;
  }

  @Transactional(readOnly = true)
  public MigrationDag findDag(Collection<MigrationFileType> fileTypesToInclude) {
    Objects.requireNonNull(fileTypesToInclude, "fileTypesToInclude must not be null");
    if (fileTypesToInclude.isEmpty()) {
      throw new IllegalArgumentException("fileTypesToInclude must not be empty");
    }
    fileTypesToInclude.forEach(fileType ->
      Objects.requireNonNull(fileType, "fileTypesToInclude must not contain null")
    );
    return dagEngine.build(migrationDagDependencyRetrieveService.retrieve(), fileTypesToInclude);
  }
}
