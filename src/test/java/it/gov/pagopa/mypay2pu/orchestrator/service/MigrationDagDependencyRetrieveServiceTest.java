package it.gov.pagopa.mypay2pu.orchestrator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationDagDependencyRepository;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MigrationDagDependencyRetrieveServiceTest {

  @Mock
  private MigrationDagDependencyRepository repository;

  private MigrationDagDependencyRetrieveService service;

  @BeforeEach
  void setUp() {
    service = new MigrationDagDependencyRetrieveService(
      repository,
      new MigrationDagEngine(new KahnDagPlanner(), new ParallelLevelExecutor())
    );
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(repository);
  }

  @Test
  void buildsDagLazilyAndReusesTheCachedSnapshot() {
    List<MigrationDagDependency> dependencies = List.of(
      row("ROOT", "ROOT"),
      row("ORGANIZATIONS", "ROOT")
    );
    when(repository.findAll()).thenReturn(dependencies);

    MigrationDag firstResult = service.retrieve();
    MigrationDag secondResult = service.retrieve();

    assertSame(firstResult, secondResult);
    assertEquals(1, firstResult.nodes().size());
  }

  private MigrationDagDependency row(String fileType, String parent) {
    MigrationDagDependency dependency = mock(MigrationDagDependency.class);
    when(dependency.getFileType()).thenReturn(fileType);
    when(dependency.getDependsOn()).thenReturn(parent);
    return dependency;
  }
}
