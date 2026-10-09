package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import java.util.List;
import java.util.Objects;

/**
 * A functional node in the dependency graph, with its persisted parents.
 * {@code ROOT} is retained as a technical parent and is not a file type.
 * The level is the maximum depth from ROOT across the node's reachable parent paths.
 */
public record MigrationDagNode(
  MigrationFileType fileType,
  List<String> dependsOn,
  int level,
  Integer maxExecutions
) {
  public MigrationDagNode {
    Objects.requireNonNull(fileType, "fileType must not be null");
    dependsOn = List.copyOf(dependsOn);
    if (level < 1) {
      throw new IllegalArgumentException("DAG node level must be positive");
    }
  }
}
