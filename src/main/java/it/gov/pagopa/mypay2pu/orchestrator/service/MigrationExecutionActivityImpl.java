package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationExecutionActivityImpl implements MigrationExecutionActivity {

  private final MigrationExecutionRepository migrationExecutionRepository;

  public MigrationExecutionActivityImpl(MigrationExecutionRepository migrationExecutionRepository) {
    this.migrationExecutionRepository = migrationExecutionRepository;
  }

  @Override
  @Transactional
  public MigrationExecution save(MigrationExecution execution) {
    return migrationExecutionRepository.save(execution);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MigrationExecution> findById(UUID id) {
    return migrationExecutionRepository.findById(id);
  }
}
