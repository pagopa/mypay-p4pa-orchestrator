package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionLevel;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionNode;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionPlan;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationDagEngineTest {
  @Mock
  private DagPlanner dagPlannerMock;

  @Mock
  private LevelExecutor levelExecutorMock;

  private MigrationDagEngine engine;
  private List<MigrationDagDependency> delegatedRows;

  @BeforeEach
  void setUp() {
    engine = new MigrationDagEngine(new KahnDagPlanner(), new ParallelLevelExecutor());
  }

  @AfterEach
  void tearDown() {
    if (delegatedRows != null) {
      verify(dagPlannerMock).buildExecutionPlan(delegatedRows);
    }
    verifyNoMoreInteractions(dagPlannerMock, levelExecutorMock);
  }

  @Test
  void topologicallyOrdersMultipleParentsAndPrunesWithoutAddingAncestors() {
    MigrationDag dag = engine.filterTree(engine.buildHierarchyTree(
      List.of(
        row("DEBT_POSITIONS_PAID", "DEBT_POSITIONS_TYPE_ORG", null),
        row("ROOT", "ROOT", 0),
        row("DEBT_POSITIONS_TYPE_ORG", "DEBT_POSITIONS_TYPE", 1),
        row("ORGANIZATIONS", "ROOT", 1),
        row("DEBT_POSITIONS_TYPE", "ROOT", 1),
        row("DEBT_POSITIONS_PAID", "ORGANIZATIONS", null)
      )),
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
    MigrationDag dag = engine.filterTree(engine.buildHierarchyTree(
      List.of(
        row("ROOT", "ROOT", 0),
        row("DEBT_POSITIONS", "MISSING_PARENT", null)
      )),
      List.of(MigrationFileType.DEBT_POSITIONS)
    );

    assertTrue(dag.nodes().isEmpty());
  }

  @Test
  void traversesUnrequestedIntermediateRowsBeforePruning() {
    MigrationDag dag = engine.filterTree(engine.buildHierarchyTree(
      List.of(
        row("ROOT", "ROOT", 0),
        row("LEGACY_INTERMEDIATE", "ROOT", 1),
        row("DEBT_POSITIONS", "LEGACY_INTERMEDIATE", null)
      )),
      List.of(MigrationFileType.DEBT_POSITIONS)
    );

    assertEquals(1, dag.nodes().size());
    assertEquals(2, dag.nodes().getFirst().level());
    assertEquals(List.of("LEGACY_INTERMEDIATE"), dag.nodes().getFirst().dependsOn());
  }

  @Test
  void detectsCyclesReachableFromTheTechnicalRoot() {
    List<MigrationDagDependency> dependencies = List.of(
      row("ROOT", "ROOT", 0),
      row("ORGANIZATIONS", "ROOT", 1),
      row("DEBT_POSITIONS_TYPE", "ORGANIZATIONS", 1),
      row("ORGANIZATIONS", "DEBT_POSITIONS_TYPE", 1)
    );

    assertThrows(IllegalStateException.class, () -> engine.buildHierarchyTree(
      dependencies
    ));
  }

  @Test
  void delegatesExecutionPlanBuildingToDagPlanner() {
    MigrationDagEngine customEngine = new MigrationDagEngine(dagPlannerMock, levelExecutorMock);

    List<MigrationDagDependency> rows = List.of(mock(MigrationDagDependency.class));
    delegatedRows = rows;
    ExecutionPlan expectedPlan = new ExecutionPlan(List.of());
    when(dagPlannerMock.buildExecutionPlan(rows)).thenReturn(expectedPlan);

    ExecutionPlan result = customEngine.buildExecutionPlan(rows);

    assertEquals(expectedPlan, result);
  }

  @Test
  void executesPlanLevelByLevelInOrder() {
    ExecutionNode node1 = new ExecutionNode(MigrationFileType.ORGANIZATIONS, List.of("ROOT"), 1, 1);
    ExecutionNode node2 = new ExecutionNode(MigrationFileType.DEBT_POSITIONS_TYPE, List.of("ROOT"), 1, 1);
    ExecutionNode node3 = new ExecutionNode(MigrationFileType.DEBT_POSITIONS, List.of("DEBT_POSITIONS_TYPE_ORG"), 2, null);

    ExecutionLevel level1 = new ExecutionLevel(1, List.of(node1, node2));
    ExecutionLevel level2 = new ExecutionLevel(2, List.of(node3));
    ExecutionPlan plan = new ExecutionPlan(List.of(level1, level2));

    List<MigrationFileType> executedTypes = Collections.synchronizedList(new ArrayList<>());
    engine.executePlan(plan, node -> executedTypes.add(node.fileType()));

    assertEquals(3, executedTypes.size());
    assertTrue(executedTypes.contains(MigrationFileType.ORGANIZATIONS));
    assertTrue(executedTypes.contains(MigrationFileType.DEBT_POSITIONS_TYPE));
    assertEquals(MigrationFileType.DEBT_POSITIONS, executedTypes.get(2));
  }

  private MigrationDagDependency row(String fileType, String parent, Integer maxExecutions) {
    MigrationDagDependency row = mock(MigrationDagDependency.class);
    when(row.getFileType()).thenReturn(fileType);
    when(row.getDependsOn()).thenReturn(parent);
    if (maxExecutions != null && !"ROOT".equals(fileType)) {
      when(row.getMaxExecutions()).thenReturn(maxExecutions);
    }
    return row;
  }
}
