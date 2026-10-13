package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionLevel;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionNode;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionPlan;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KahnDagPlannerTest {

  private final KahnDagPlanner planner = new KahnDagPlanner();

  @Test
  void buildsExecutionPlanWithCorrectDeterministicLevels() {
    List<MigrationDagDependency> rows = standardRows();
    ExecutionPlan plan = planner.buildExecutionPlan(rows);

    // Levels expected:
    // Level 1: ORGANIZATIONS, DEBT_POSITIONS_TYPE
    // Level 2: ORG_SIL_SERVICES, DEBT_POSITIONS_TYPE_ORG
    // Level 3: ASSESSMENTS_REGISTRY, DEBT_POSITIONS, DEBT_POSITIONS_PAID, DEBT_POSITIONS_TYPE_ORG_OPERATORS,
    //          PAYMENT_NOTIFICATION, PAYMENTS_REPORTING, TREASURY_CSV_COMPLETE
    // Level 4: ASSESSMENTS
    assertEquals(4, plan.levels().size());

    ExecutionLevel level1 = plan.levels().get(0);
    assertEquals(1, level1.level());
    assertEquals(List.of(MigrationFileType.DEBT_POSITIONS_TYPE, MigrationFileType.ORGANIZATIONS),
        level1.nodes().stream().map(ExecutionNode::fileType).toList());

    ExecutionLevel level2 = plan.levels().get(1);
    assertEquals(2, level2.level());
    assertEquals(List.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, MigrationFileType.ORG_SIL_SERVICES),
        level2.nodes().stream().map(ExecutionNode::fileType).toList());

    ExecutionLevel level3 = plan.levels().get(2);
    assertEquals(3, level3.level());
    Set<MigrationFileType> level3Types = level3.nodes().stream()
        .map(ExecutionNode::fileType)
        .collect(Collectors.toSet());
    assertEquals(Set.of(
        MigrationFileType.ASSESSMENTS_REGISTRY,
        MigrationFileType.DEBT_POSITIONS,
        MigrationFileType.DEBT_POSITIONS_PAID,
        MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS,
        MigrationFileType.PAYMENT_NOTIFICATION,
        MigrationFileType.PAYMENTS_REPORTING,
        MigrationFileType.TREASURY_CSV_COMPLETE
    ), level3Types);

    ExecutionLevel level4 = plan.levels().get(3);
    assertEquals(4, level4.level());
    assertEquals(List.of(MigrationFileType.ASSESSMENTS),
        level4.nodes().stream().map(ExecutionNode::fileType).toList());
  }

  @Test
  void detectsCyclesReachableFromRootAndFailsFast() {
    List<MigrationDagDependency> rows = List.of(
        row("ROOT", "ROOT", 0),
        row("ORGANIZATIONS", "ROOT", 1),
        row("DEBT_POSITIONS_TYPE", "ORGANIZATIONS", 1),
        row("ORGANIZATIONS", "DEBT_POSITIONS_TYPE", 1)
    );

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> planner.buildExecutionPlan(rows));
    assertTrue(exception.getMessage().contains("Cycle detected"));
  }

  @Test
  void ignoresNodesNotReachableFromRoot() {
    List<MigrationDagDependency> rows = List.of(
        row("ROOT", "ROOT", 0),
        row("DEBT_POSITIONS", "DISCONNECTED", null)
    );

    ExecutionPlan plan = planner.buildExecutionPlan(rows);
    assertTrue(plan.levels().isEmpty());
    assertTrue(plan.allNodes().isEmpty());
  }

  @Test
  void traversesUnrequestedIntermediateRowsAndPreservesMaxLevel() {
    List<MigrationDagDependency> rows = List.of(
        row("ROOT", "ROOT", 0),
        row("LEGACY_INTERMEDIATE", "ROOT", 1),
        row("DEBT_POSITIONS", "LEGACY_INTERMEDIATE", null)
    );

    ExecutionPlan plan = planner.buildExecutionPlan(rows);
    assertEquals(1, plan.levels().size());
    ExecutionNode node = plan.levels().getFirst().nodes().getFirst();
    assertEquals(MigrationFileType.DEBT_POSITIONS, node.fileType());
    assertEquals(2, node.level());
    assertEquals(List.of("LEGACY_INTERMEDIATE"), node.dependsOn());
    assertNull(node.maxExecutions());
  }

  @Test
  void returnsEmptyPlanWhenRootRowIsMissing() {
    List<MigrationDagDependency> rows = List.of(
        row("ORGANIZATIONS", "ROOT", 1)
    );

    ExecutionPlan plan = planner.buildExecutionPlan(rows);
    assertTrue(plan.levels().isEmpty());
  }

  @Test
  void filterPreservesLevelGroupingForRetainedTypes() {
    List<MigrationDagDependency> rows = standardRows();
    ExecutionPlan plan = planner.buildExecutionPlan(rows);

    ExecutionPlan filtered = plan.filter(List.of(
        MigrationFileType.ORGANIZATIONS,
        MigrationFileType.ASSESSMENTS
    ));

    assertEquals(2, filtered.levels().size());
    assertEquals(1, filtered.levels().get(0).level());
    assertEquals(List.of(MigrationFileType.ORGANIZATIONS),
        filtered.levels().get(0).nodes().stream().map(ExecutionNode::fileType).toList());
    assertEquals(4, filtered.levels().get(1).level());
    assertEquals(List.of(MigrationFileType.ASSESSMENTS),
        filtered.levels().get(1).nodes().stream().map(ExecutionNode::fileType).toList());
  }

  private List<MigrationDagDependency> standardRows() {
    return List.of(
        row("ROOT", "ROOT", 0),
        row("ORGANIZATIONS", "ROOT", 1),
        row("ORG_SIL_SERVICES", "ORGANIZATIONS", 1),
        row("DEBT_POSITIONS_TYPE", "ROOT", 1),
        row("DEBT_POSITIONS_TYPE_ORG", "ORGANIZATIONS", 1),
        row("DEBT_POSITIONS_TYPE_ORG", "DEBT_POSITIONS_TYPE", 1),
        row("DEBT_POSITIONS_TYPE_ORG_OPERATORS", "DEBT_POSITIONS_TYPE_ORG", 1),
        row("DEBT_POSITIONS", "DEBT_POSITIONS_TYPE_ORG", null),
        row("DEBT_POSITIONS_PAID", "DEBT_POSITIONS_TYPE_ORG", null),
        row("PAYMENT_NOTIFICATION", "DEBT_POSITIONS_TYPE_ORG", null),
        row("PAYMENTS_REPORTING", "DEBT_POSITIONS_TYPE_ORG", null),
        row("TREASURY_CSV_COMPLETE", "DEBT_POSITIONS_TYPE_ORG", null),
        row("ASSESSMENTS_REGISTRY", "DEBT_POSITIONS_TYPE_ORG", null),
        row("ASSESSMENTS", "DEBT_POSITIONS_PAID", null)
    );
  }

  private MigrationDagDependency row(String fileType, String parent, Integer maxExecutions) {
    MigrationDagDependency row = mock(MigrationDagDependency.class);
    when(row.getFileType()).thenReturn(fileType);
    when(row.getDependsOn()).thenReturn(parent);
    if (!"ROOT".equals(fileType)) {
      when(row.getMaxExecutions()).thenReturn(maxExecutions);
    }
    return row;
  }
}
