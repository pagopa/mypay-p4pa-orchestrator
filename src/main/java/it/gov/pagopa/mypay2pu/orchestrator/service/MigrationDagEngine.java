package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionLevel;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionNode;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionPlan;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Orchestration engine for the migration DAG.
 * Delegates plan construction to {@link DagPlanner} and coordinates execution by level.
 */
@Component
public class MigrationDagEngine {

  private final DagPlanner dagPlanner;
  private final LevelExecutor levelExecutor;

  public MigrationDagEngine(DagPlanner dagPlanner, LevelExecutor levelExecutor) {
    this.dagPlanner = dagPlanner;
    this.levelExecutor = levelExecutor;
  }

  /**
   * Builds an immutable execution plan from the given repository dependencies.
   *
   * @param dependencies all dependency rows returned by the repository
   * @return the immutable execution plan partitioned into parallel levels
   */
  public ExecutionPlan buildExecutionPlan(Collection<MigrationDagDependency> dependencies) {
    return dagPlanner.buildExecutionPlan(dependencies);
  }

  /**
   * Executes the plan level-by-level, running nodes within each level via the LevelExecutor.
   *
   * @param plan the execution plan to execute
   * @param nodeTask the task to run for each node
   */
  public void executePlan(ExecutionPlan plan, Consumer<ExecutionNode> nodeTask) {
    Objects.requireNonNull(plan, "plan must not be null");
    Objects.requireNonNull(nodeTask, "nodeTask must not be null");

    for (ExecutionLevel level : plan.levels()) {
      levelExecutor.executeLevel(level, nodeTask);
    }
  }

  /**
   * Reconstructs the complete functional DAG reachable from ROOT.
   * Provided for backward compatibility; delegates to {@link DagPlanner}.
   *
   * @param dependencies all dependency rows returned by the repository
   * @return the immutable, topologically ordered DAG
   */
  public MigrationDag buildHierarchyTree(Collection<MigrationDagDependency> dependencies) {
    return buildExecutionPlan(dependencies).toMigrationDag();
  }

  /**
   * Filters an already built DAG without rebuilding or rereading its source rows.
   *
   * @param dag complete functional DAG
   * @param fileTypesToInclude the functional file types to keep in the resulting DAG
   * @return filtered MigrationDag
   */
  public MigrationDag filterTree(MigrationDag dag, Collection<MigrationFileType> fileTypesToInclude) {
    Objects.requireNonNull(dag, "dag must not be null");
    Objects.requireNonNull(fileTypesToInclude, "fileTypesToInclude must not be null");
    if (fileTypesToInclude.isEmpty()) {
      throw new IllegalArgumentException("fileTypesToInclude must not be empty");
    }

    Set<String> requestedValues = fileTypesToInclude.stream()
        .map(MigrationFileType::getValue)
        .collect(Collectors.toSet());

    return new MigrationDag(dag.nodes().stream()
        .filter(node -> requestedValues.contains(node.fileType().getValue()))
        .toList());
  }
}

