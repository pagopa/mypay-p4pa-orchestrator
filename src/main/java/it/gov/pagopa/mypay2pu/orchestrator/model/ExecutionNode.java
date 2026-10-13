package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import java.util.List;
import java.util.Objects;

/**
 * An execution node representing a functional file type in the execution plan.
 *
 * @param fileType functional file type to be executed
 * @param dependsOn list of parent dependencies (functional types or technical ROOT)
 * @param level the execution level (0-based or 1-based depth)
 * @param maxExecutions maximum executions allowed, or null if unlimited
 */
public record ExecutionNode(MigrationFileType fileType,
                            List<String> dependsOn,
                            int level,
                            Integer maxExecutions) {

  public ExecutionNode {
    Objects.requireNonNull(fileType, "fileType must not be null");
    dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
  }

  /**
   * Adapts a MigrationDagNode to an ExecutionNode.
   */
  public static ExecutionNode fromDagNode(MigrationDagNode dagNode) {
    Objects.requireNonNull(dagNode, "dagNode must not be null");
    return new ExecutionNode(
        dagNode.fileType(),
        dagNode.dependsOn(),
        dagNode.level(),
        dagNode.maxExecutions()
    );
  }

  /**
   * Converts this ExecutionNode to a MigrationDagNode.
   */
  public MigrationDagNode toDagNode() {
    return new MigrationDagNode(fileType, dependsOn, level, maxExecutions);
  }
}
