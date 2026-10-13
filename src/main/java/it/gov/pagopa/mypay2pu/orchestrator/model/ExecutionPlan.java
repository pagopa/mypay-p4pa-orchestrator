package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable execution plan composed of sequential dependency levels,
 * where nodes within each level can be executed in parallel.
 *
 * @param levels list of execution levels ordered from first to last
 */
public record ExecutionPlan(List<ExecutionLevel> levels) {

  public ExecutionPlan {
    levels = List.copyOf(Objects.requireNonNull(levels, "levels must not be null"));
  }

  /**
   * Returns all execution nodes across all levels in execution plan order.
   */
  public List<ExecutionNode> allNodes() {
    return levels.stream()
        .flatMap(level -> level.nodes().stream())
        .toList();
  }

  /**
   * Converts the execution plan to a MigrationDag for backward compatibility.
   */
  public MigrationDag toMigrationDag() {
    List<MigrationDagNode> dagNodes = allNodes().stream()
        .map(ExecutionNode::toDagNode)
        .toList();
    return new MigrationDag(dagNodes);
  }

  /**
   * Returns a filtered copy of this ExecutionPlan keeping only the requested file types.
   * Levels that become empty after filtering are omitted.
   *
   * @param fileTypesToInclude non-null, non-empty collection of file types to retain
   * @return a new filtered ExecutionPlan
   */
  public ExecutionPlan filter(Collection<MigrationFileType> fileTypesToInclude) {
    Objects.requireNonNull(fileTypesToInclude, "fileTypesToInclude must not be null");
    if (fileTypesToInclude.isEmpty()) {
      throw new IllegalArgumentException("fileTypesToInclude must not be empty");
    }

    Set<MigrationFileType> requested = Set.copyOf(fileTypesToInclude);
    List<ExecutionLevel> filteredLevels = levels.stream()
        .map(level -> {
          List<ExecutionNode> matchingNodes = level.nodes().stream()
              .filter(node -> requested.contains(node.fileType()))
              .toList();
          return matchingNodes.isEmpty() ? null : new ExecutionLevel(level.level(), matchingNodes);
        })
        .filter(Objects::nonNull)
        .toList();

    return new ExecutionPlan(filteredLevels);
  }
}
