package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagNode;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Validates dependency requirements for the file types participating in a migration cycle.
 */
@Component
public class MigrationDagValidator {
  private static final String ROOT = "ROOT";

  /**
   * Verifies that the initial cycle includes each requested type's functional parents.
   *
   * @param dag requested DAG, retaining persisted parents even when they were filtered out
   * @param fileTypesInCycle types requested for the initial cycle
   */
  public void validateInitialCycleDependencies(MigrationDag dag,
                                               Collection<MigrationFileType> fileTypesInCycle) {
    Set<String> cycleTypes = fileTypeValues(fileTypesInCycle);
    Map<String, MigrationDagNode> nodesByType = nodesByType(dag);
    validateDagCoverage(cycleTypes, nodesByType);

    Set<String> missingDependencies = new TreeSet<>();
    for (String fileType : cycleTypes) {
      MigrationDagNode node = nodesByType.get(fileType);
      for (String parent : node.dependsOn()) {
        if (!ROOT.equals(parent) && !cycleTypes.contains(parent)) {
          missingDependencies.add(fileType + " requires " + parent);
        }
      }
    }

    if (!missingDependencies.isEmpty()) {
      throw new InvalidValueException(
        "MISSING_MIGRATION_DAG_DEPENDENCY",
        "Initial cycle is missing required dependencies: " + String.join(", ", missingDependencies)
      );
    }
  }

  /**
   * Rejects a cycle when a parent participating in that same cycle has failed.
   *
   * Parents omitted from the current cycle, including parents completed in a previous cycle,
   * do not block the current cycle.
   *
   * @param dag DAG containing the types selected for this cycle
   * @param fileTypesInCycle types participating in the current cycle
   * @param statusByFileType latest known execution status for current-cycle types
   */
  public void validateCurrentCycleParents(MigrationDag dag,
                                          Collection<MigrationFileType> fileTypesInCycle,
                                          Map<MigrationFileType, MigrationExecutionStatus> statusByFileType) {
    Set<String> cycleTypes = fileTypeValues(fileTypesInCycle);
    Map<String, MigrationDagNode> nodesByType = nodesByType(dag);
    validateDagCoverage(cycleTypes, nodesByType);
    Objects.requireNonNull(statusByFileType, "statusByFileType must not be null");

    Set<String> failedDependencies = new TreeSet<>();
    for (String fileType : cycleTypes) {
      MigrationDagNode node = nodesByType.get(fileType);
      for (String parent : node.dependsOn()) {
        if (cycleTypes.contains(parent)
          && statusByFileType.get(nodesByType.get(parent).fileType()) == MigrationExecutionStatus.FAILED) {
          failedDependencies.add(fileType + " depends on failed parent " + parent);
        }
      }
    }

    if (!failedDependencies.isEmpty()) {
      throw new ConflictException(
        "MIGRATION_DAG_PARENT_FAILED",
        "Current cycle cannot continue: " + String.join(", ", failedDependencies)
      );
    }
  }

  private Set<String> fileTypeValues(Collection<MigrationFileType> fileTypes) {
    Objects.requireNonNull(fileTypes, "fileTypesInCycle must not be null");
    if (fileTypes.isEmpty()) {
      throw new IllegalArgumentException("fileTypesInCycle must not be empty");
    }
    return fileTypes.stream()
      .map(fileType -> Objects.requireNonNull(fileType, "fileTypesInCycle must not contain null").getValue())
      .collect(Collectors.toCollection(TreeSet::new));
  }

  private Map<String, MigrationDagNode> nodesByType(MigrationDag dag) {
    Objects.requireNonNull(dag, "dag must not be null");
    return dag.nodes().stream()
      .collect(Collectors.toMap(node -> node.fileType().getValue(), node -> node));
  }

  private void validateDagCoverage(Set<String> fileTypes, Map<String, MigrationDagNode> nodesByType) {
    Set<String> missingTypes = new TreeSet<>(fileTypes);
    missingTypes.removeAll(nodesByType.keySet());
    if (!missingTypes.isEmpty()) {
      throw new InvalidValueException(
        "MISSING_MIGRATION_DAG_FILE_TYPE",
        "Requested file types are not present in the migration DAG: " + String.join(", ", missingTypes)
      );
    }
  }
}
