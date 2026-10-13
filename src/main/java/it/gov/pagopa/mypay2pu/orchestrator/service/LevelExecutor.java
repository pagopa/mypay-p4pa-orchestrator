package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionLevel;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionNode;
import java.util.function.Consumer;

/**
 * Strategy/executor for executing an ExecutionLevel, possibly in parallel.
 */
@FunctionalInterface
public interface LevelExecutor {

  /**
   * Executes the nodes in the given level, blocking until all nodes in this level have completed.
   *
   * @param level the level to execute
   * @param nodeTask the action to perform for each execution node
   */
  void executeLevel(ExecutionLevel level, Consumer<ExecutionNode> nodeTask);

}
