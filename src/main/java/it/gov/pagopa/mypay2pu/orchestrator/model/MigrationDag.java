package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, filtered dependency graph with no execution state or global level barriers.
 * Parent relationships are retained when parents are filtered out; siblings may run in parallel.
 * Dependency validation and cycle selection are handled outside this model.
 *
 * @param nodes requested functional nodes ordered by level then file type; excludes technical ROOT
 */
public record MigrationDag(List<MigrationDagNode> nodes) {
  public MigrationDag {
    nodes = List.copyOf(nodes);
  }

  /**
   * Returns included children of the technical ROOT, preserving node order.
   * Nodes with filtered-out functional parents are not promoted to roots.
   */
  public List<MigrationDagNode> rootNodes() {
    return nodes.stream()
      .filter(node -> node.dependsOn().contains("ROOT"))
      .toList();
  }

  /**
   * Returns included direct children in topological order, even if the parent is not included.
   * These siblings may run in parallel once their current-cycle dependency is satisfied.
   *
   * @param parent non-null functional parent type
   */
  public List<MigrationDagNode> childrenOf(MigrationFileType parent) {
    Objects.requireNonNull(parent, "parent must not be null");
    return nodes.stream()
      .filter(node -> node.dependsOn().contains(parent.getValue()))
      .toList();
  }
}
