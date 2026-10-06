package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, filtered DB hierarchy with no execution state or global level barriers.
 * Types await parents included in the current cycle; siblings may run in parallel.
 * Persisted parent references are retained even when parents are filtered out.
 * Dependency validation and cycle selection are handled outside this model.
 *
 * @param nodes requested functional nodes in DB result order; excludes the technical ROOT node
 */
public record MigrationDag(List<MigrationDagDependency> nodes) {
  public MigrationDag {
    nodes = List.copyOf(nodes);
  }

  /**
   * Returns included children of the technical ROOT, preserving node order.
   * Nodes with filtered-out functional parents are not promoted to roots.
   */
  public List<MigrationDagDependency> rootNodes() {
    return nodes.stream()
      .filter(node -> "ROOT".equals(node.dependsOn()))
      .toList();
  }

  /**
   * Returns included direct children in node order, even if the parent is not included.
   * These siblings may run in parallel once their current-cycle dependency is satisfied.
   *
   * @param parent non-null functional parent type
   */
  public List<MigrationDagDependency> childrenOf(MigrationFileType parent) {
    Objects.requireNonNull(parent, "parent must not be null");
    return nodes.stream()
      .filter(node -> parent.getValue().equals(node.dependsOn()))
      .toList();
  }
}
