package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dao.MigrationDagDependencyDao;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigrationDagServiceTest {
  private final MigrationDagDependencyDao dao = mock(MigrationDagDependencyDao.class);
  private final MigrationDagService service = new MigrationDagService(dao);

  @Test
  void exposesDatabaseHierarchyAndParallelSiblingsWithoutGlobalLevelBarriers() {
    MigrationDagDependency types = dependency("DEBT_POSITIONS_TYPE", "ROOT", 1, 1);
    MigrationDagDependency organizations = dependency("ORGANIZATIONS", "ROOT", 1, 1);
    MigrationDagDependency typeOrg = dependency("DEBT_POSITIONS_TYPE_ORG", "ORGANIZATIONS", 2, 1);
    MigrationDagDependency sil = dependency("ORG_SIL_SERVICES", "ORGANIZATIONS", 2, 1);
    MigrationDagDependency debts = dependency("DEBT_POSITIONS", "DEBT_POSITIONS_TYPE_ORG", 3, null);
    MigrationDagDependency paid = dependency("DEBT_POSITIONS_PAID", "DEBT_POSITIONS_TYPE_ORG", 3, null);
    MigrationDagDependency assessments = dependency("ASSESSMENTS", "DEBT_POSITIONS_PAID", 4, null);
    List<MigrationDagDependency> rows = List.of(types, organizations, typeOrg, sil, debts, paid, assessments);
    List<MigrationFileType> includedTypes = rows.stream().map(MigrationDagDependency::fileType).toList();
    when(dao.findDag(includedTypes)).thenReturn(rows);

    MigrationDag dag = service.findDag(includedTypes);

    assertEquals(rows, dag.nodes());
    assertEquals(List.of(types, organizations), dag.rootNodes());
    assertEquals(List.of(typeOrg, sil), dag.childrenOf(organizations.fileType()));
    assertEquals(List.of(debts, paid), dag.childrenOf(typeOrg.fileType()));
    assertEquals(List.of(assessments), dag.childrenOf(paid.fileType()));
    assertEquals(List.of(), dag.childrenOf(types.fileType()));
    assertNull(paid.maxExecutions());
    assertEquals(4, assessments.level());
    assertThrows(UnsupportedOperationException.class, () -> dag.nodes().add(organizations));
    assertThrows(UnsupportedOperationException.class, () -> dag.rootNodes().clear());
    assertThrows(UnsupportedOperationException.class, () -> dag.childrenOf(typeOrg.fileType()).clear());
    verify(dao).findDag(includedTypes);
  }

  @Test
  void preservesFilteredOutParentWithoutPromotingChildToRootOrAddingAncestors() {
    MigrationDagDependency assessments = dependency("ASSESSMENTS", "DEBT_POSITIONS_PAID", 4, null);
    List<MigrationFileType> includedTypes = List.of(assessments.fileType());
    when(dao.findDag(includedTypes)).thenReturn(List.of(assessments));

    MigrationDag dag = service.findDag(includedTypes);

    assertEquals(List.of(assessments), dag.nodes());
    assertEquals(List.of(), dag.rootNodes());
    assertEquals(List.of(assessments), dag.childrenOf(MigrationFileType.DEBT_POSITIONS_PAID));
    assertEquals("DEBT_POSITIONS_PAID", dag.nodes().getFirst().dependsOn());
    assertEquals(4, dag.nodes().getFirst().level());
  }

  @Test
  void exposesEmptyHierarchyWhenNoRowsAreReturned() {
    List<MigrationFileType> includedTypes = List.of(MigrationFileType.ORGANIZATIONS);
    when(dao.findDag(includedTypes)).thenReturn(List.of());

    MigrationDag dag = service.findDag(includedTypes);

    assertEquals(List.of(), dag.nodes());
    assertEquals(List.of(), dag.rootNodes());
    assertEquals(List.of(), dag.childrenOf(MigrationFileType.ORGANIZATIONS));
  }

  private MigrationDagDependency dependency(String fileType, String parent, int level, Integer maxExecutions) {
    return new MigrationDagDependency(MigrationFileType.fromValue(fileType), parent, level, maxExecutions);
  }
}
