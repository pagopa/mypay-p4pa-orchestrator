package it.gov.pagopa.mypay2pu.orchestrator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
    service = new MigrationDagDependencyRetrieveService(repository);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(repository);
  }

  @Test
  void retrievesDependenciesLazilyAndReusesTheCachedList() {
    List<MigrationDagDependency> dependencies = List.of(new MigrationDagDependency());
    when(repository.findAll()).thenReturn(dependencies);

    List<MigrationDagDependency> firstResult = service.retrieve();
    List<MigrationDagDependency> secondResult = service.retrieve();

    assertSame(firstResult, secondResult);
    assertEquals(dependencies, firstResult);
    assertThrows(UnsupportedOperationException.class, () -> firstResult.clear());
  }
}
