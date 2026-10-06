package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dao.MigrationDagDependencyDao;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import java.util.Collection;
import org.springframework.stereotype.Service;

@Service
public class MigrationDagService {
  private final MigrationDagDependencyDao migrationDagDependencyDao;

  public MigrationDagService(MigrationDagDependencyDao migrationDagDependencyDao) {
    this.migrationDagDependencyDao = migrationDagDependencyDao;
  }

  public MigrationDag findDag(Collection<MigrationFileType> fileTypesToInclude) {
    return new MigrationDag(migrationDagDependencyDao.findDag(fileTypesToInclude));
  }
}
