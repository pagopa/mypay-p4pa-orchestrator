package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationExecutionActivityImplTest {

  @Mock
  private MigrationExecutionRepository migrationExecutionRepository;

  private MigrationExecutionActivityImpl activity;

  @BeforeEach
  void init() {
    activity = new MigrationExecutionActivityImpl(migrationExecutionRepository);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationExecutionRepository);
  }

  @Test
  void savesExecution() {
    MigrationExecution execution = MigrationExecution.builder()
        .id(UUID.randomUUID())
        .build();
    when(migrationExecutionRepository.save(execution)).thenReturn(execution);

    MigrationExecution result = activity.save(execution);

    assertSame(execution, result);
    verify(migrationExecutionRepository).save(execution);
  }

  @Test
  void findsExecutionById() {
    UUID id = UUID.randomUUID();
    MigrationExecution execution = MigrationExecution.builder()
        .id(id)
        .build();
    when(migrationExecutionRepository.findById(id)).thenReturn(Optional.of(execution));

    Optional<MigrationExecution> result = activity.findById(id);

    assertSame(execution, result.orElseThrow());
    verify(migrationExecutionRepository).findById(id);
  }
}
