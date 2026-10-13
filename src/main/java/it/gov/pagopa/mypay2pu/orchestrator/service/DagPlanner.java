package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionPlan;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import java.util.Collection;

/**
 * Contract for planning an execution plan from persisted DAG dependencies.
 */
public interface DagPlanner {

  /**
   * Builds an immutable execution plan from the given repository dependencies.
   *
   * @param dependencies collection of persisted dependency rows
   * @return immutable ExecutionPlan with partitioned parallel levels
   */
  ExecutionPlan buildExecutionPlan(Collection<MigrationDagDependency> dependencies);

}
