package it.gov.pagopa.mypay2pu.orchestrator.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents an execution level grouping nodes that can be executed in parallel.
 *
 * @param level the level index
 * @param nodes the list of execution nodes ready to execute at this level
 */
public record ExecutionLevel(int level, List<ExecutionNode> nodes) {
  public ExecutionLevel {
    nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes must not be null"));
  }
}
