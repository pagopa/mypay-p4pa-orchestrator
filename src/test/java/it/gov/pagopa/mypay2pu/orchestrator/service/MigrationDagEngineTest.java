package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigrationDagEngineTest {
  private final MigrationDagEngine engine = new MigrationDagEngine();

  @Test
  void topologicallyOrdersMultipleParentsAndPrunesWithoutAddingAncestors() {
    MigrationDag dag = engine.build(
      List.of(
        row("DEBT_POSITIONS_PAID", "DEBT_POSITIONS_TYPE_ORG", null),
        row("ROOT", "ROOT", 0),
        row("DEBT_POSITIONS_TYPE_ORG", "DEBT_POSITIONS_TYPE", 1),
        row("ORGANIZATIONS", "ROOT", 1),
        row("DEBT_POSITIONS_TYPE", "ROOT", 1),
        row("DEBT_POSITIONS_PAID", "ORGANIZATIONS", null)
      ),
      List.of(MigrationFileType.DEBT_POSITIONS_PAID)
    );

    assertEquals(1, dag.nodes().size());
    MigrationDagNode paid = dag.nodes().getFirst();
    assertEquals(MigrationFileType.DEBT_POSITIONS_PAID, paid.fileType());
    assertEquals(List.of("DEBT_POSITIONS_TYPE_ORG", "ORGANIZATIONS"), paid.dependsOn());
    assertEquals(3, paid.level());
    assertTrue(dag.rootNodes().isEmpty());
  }

  @Test
  void doesNotReturnTypesNotReachableFromTheTechnicalRoot() {
    MigrationDag dag = engine.build(
      List.of(
        row("ROOT", "ROOT", 0),
        row("DEBT_POSITIONS", "MISSING_PARENT", null)
      ),
      List.of(MigrationFileType.DEBT_POSITIONS)
    );

    assertTrue(dag.nodes().isEmpty());
  }

  @Test
  void traversesUnrequestedIntermediateRowsBeforePruning() {
    MigrationDag dag = engine.build(
      List.of(
        row("ROOT", "ROOT", 0),
        row("LEGACY_INTERMEDIATE", "ROOT", 1),
        row("DEBT_POSITIONS", "LEGACY_INTERMEDIATE", null)
      ),
      List.of(MigrationFileType.DEBT_POSITIONS)
    );

    assertEquals(1, dag.nodes().size());
    assertEquals(2, dag.nodes().getFirst().level());
    assertEquals(List.of("LEGACY_INTERMEDIATE"), dag.nodes().getFirst().dependsOn());
  }

  @Test
  void detectsCyclesReachableFromTheTechnicalRoot() {
    assertThrows(IllegalStateException.class, () -> engine.build(
      List.of(
        row("ROOT", "ROOT", 0),
        row("ORGANIZATIONS", "ROOT", 1),
        row("DEBT_POSITIONS_TYPE", "ORGANIZATIONS", 1),
        row("ORGANIZATIONS", "DEBT_POSITIONS_TYPE", 1)
      ),
      List.of(MigrationFileType.ORGANIZATIONS)
    ));
  }

  private MigrationDagDependency row(String fileType, String parent, Integer maxExecutions) {
    MigrationDagDependency row = mock(MigrationDagDependency.class);
    when(row.getFileType()).thenReturn(fileType);
    when(row.getDependsOn()).thenReturn(parent);
    when(row.getMaxExecutions()).thenReturn(maxExecutions);
    return row;
  }
}
