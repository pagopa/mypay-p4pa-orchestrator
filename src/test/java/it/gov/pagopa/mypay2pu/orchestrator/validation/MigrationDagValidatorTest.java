package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationDagValidatorTest {
  private final MigrationDagValidator validator = new MigrationDagValidator();

  @Test
  void acceptsInitialCycleWhenAllTransitiveDependenciesAreRequested() {
    MigrationDag dag = dag(
      node(MigrationFileType.ORGANIZATIONS, List.of("ROOT")),
      node(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, List.of("ORGANIZATIONS")),
      node(MigrationFileType.DEBT_POSITIONS, List.of("DEBT_POSITIONS_TYPE_ORG"))
    );

    assertDoesNotThrow(() -> validator.validateInitialCycleDependencies(dag, List.of(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.DEBT_POSITIONS
    )));
  }

  @Test
  void rejectsInitialCycleWhenDirectOrTransitiveDependencyIsMissing() {
    MigrationDag dag = dag(
      node(MigrationFileType.ORGANIZATIONS, List.of("ROOT")),
      node(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, List.of("ORGANIZATIONS")),
      node(MigrationFileType.DEBT_POSITIONS, List.of("DEBT_POSITIONS_TYPE_ORG"))
    );

    List<MigrationFileType> cycleTypes = List.of(
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileType.ORGANIZATIONS
    );
    InvalidValueException exception = assertThrows(InvalidValueException.class,
      () -> validator.validateInitialCycleDependencies(dag, cycleTypes));

    assertEquals("MISSING_MIGRATION_DAG_DEPENDENCY", exception.getCode());
    assertEquals(
      "Initial cycle is missing required dependencies: DEBT_POSITIONS requires DEBT_POSITIONS_TYPE_ORG",
      exception.getMessage()
    );
  }

  @Test
  void rejectsInitialCycleWhenAnAncestorOfARequestedParentIsMissing() {
    MigrationDag dag = dag(
      node(MigrationFileType.ORGANIZATIONS, List.of("ROOT")),
      node(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, List.of("ORGANIZATIONS")),
      node(MigrationFileType.DEBT_POSITIONS, List.of("DEBT_POSITIONS_TYPE_ORG"))
    );

    List<MigrationFileType> cycleTypes = List.of(
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG
    );
    InvalidValueException exception = assertThrows(InvalidValueException.class,
      () -> validator.validateInitialCycleDependencies(dag, cycleTypes));

    assertEquals("MISSING_MIGRATION_DAG_DEPENDENCY", exception.getCode());
    assertEquals(
      "Initial cycle is missing required dependencies: DEBT_POSITIONS_TYPE_ORG requires ORGANIZATIONS",
      exception.getMessage()
    );
  }

  @Test
  void rejectsRequestedTypeMissingFromDag() {
    MigrationDag dag = dag(node(MigrationFileType.ORGANIZATIONS, List.of("ROOT")));
    List<MigrationFileType> cycleTypes = List.of(MigrationFileType.DEBT_POSITIONS);
    InvalidValueException exception = assertThrows(InvalidValueException.class,
      () -> validator.validateInitialCycleDependencies(dag, cycleTypes));

    assertEquals("MISSING_MIGRATION_DAG_FILE_TYPE", exception.getCode());
  }

  @Test
  void allowsParentOutsideCurrentCycleAndIgnoresItsPriorFailure() {
    MigrationDag dag = dag(node(MigrationFileType.DEBT_POSITIONS, List.of("DEBT_POSITIONS_TYPE_ORG")));

    assertDoesNotThrow(() -> validator.validateCurrentCycleParents(
      dag,
      List.of(MigrationFileType.DEBT_POSITIONS),
      Map.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, MigrationExecutionStatus.FAILED)
    ));
  }

  @Test
  void reportsConflictWhenCurrentCycleParentFailed() {
    MigrationDag dag = dag(
      node(MigrationFileType.DEBT_POSITIONS_PAID, List.of("DEBT_POSITIONS_TYPE_ORG")),
      node(MigrationFileType.ASSESSMENTS, List.of("DEBT_POSITIONS_PAID"))
    );

    List<MigrationFileType> cycleTypes = List.of(
      MigrationFileType.DEBT_POSITIONS_PAID,
      MigrationFileType.ASSESSMENTS
    );
    Map<MigrationFileType, MigrationExecutionStatus> statuses = Map.of(
      MigrationFileType.DEBT_POSITIONS_PAID,
      MigrationExecutionStatus.FAILED
    );
    ConflictException exception = assertThrows(ConflictException.class,
      () -> validator.validateCurrentCycleParents(dag, cycleTypes, statuses));

    assertEquals("MIGRATION_DAG_PARENT_FAILED", exception.getCode());
    assertEquals(
      "Current cycle cannot continue: ASSESSMENTS depends on failed parent DEBT_POSITIONS_PAID",
      exception.getMessage()
    );
  }

  @Test
  void allowsCurrentCycleParentThatHasNotFailed() {
    MigrationDag dag = dag(
      node(MigrationFileType.DEBT_POSITIONS_PAID, List.of("DEBT_POSITIONS_TYPE_ORG")),
      node(MigrationFileType.ASSESSMENTS, List.of("DEBT_POSITIONS_PAID"))
    );

    assertDoesNotThrow(() -> validator.validateCurrentCycleParents(
      dag,
      List.of(MigrationFileType.DEBT_POSITIONS_PAID, MigrationFileType.ASSESSMENTS),
      Map.of(MigrationFileType.DEBT_POSITIONS_PAID, MigrationExecutionStatus.COMPLETED)
    ));
  }

  private MigrationDag dag(MigrationDagNode... nodes) {
    return new MigrationDag(List.of(nodes));
  }

  private MigrationDagNode node(MigrationFileType fileType, List<String> parents) {
    return new MigrationDagNode(fileType, parents, 1, null);
  }
}
