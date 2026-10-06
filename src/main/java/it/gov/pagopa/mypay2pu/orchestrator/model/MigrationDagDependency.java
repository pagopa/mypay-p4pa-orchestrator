package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import java.util.Objects;

/**
 * A persisted dependency. A null maxExecutions means unlimited executions.
 * ROOT is a technical parent, not a migration file type.
 */
public record MigrationDagDependency(
  MigrationFileType fileType,
  String dependsOn,
  int level,
  Integer maxExecutions
) {
  private static final String ROOT = "ROOT";

  public MigrationDagDependency {
    Objects.requireNonNull(fileType, "fileType must not be null");
    Objects.requireNonNull(dependsOn, "dependsOn must not be null");
    if (!ROOT.equals(dependsOn)) {
      MigrationFileType.fromValue(dependsOn);
    }
    if (level < 0) {
      throw new IllegalArgumentException("DAG dependency level must be non-negative");
    }
  }
}
