package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagNode;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationDagValidator;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationDagServiceTest {

  @Mock
  private MigrationDagDependencyRetrieveService migrationDagDependencyRetrieveServiceMock;

  @Mock
  private MigrationDagValidator migrationDagValidatorMock;

  private MigrationDagService service;

  @BeforeEach
  void setUp() {
    service = new MigrationDagService(
      migrationDagDependencyRetrieveServiceMock,
      new MigrationDagEngine(new KahnDagPlanner(), new ParallelLevelExecutor()),
      migrationDagValidatorMock
    );
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(migrationDagDependencyRetrieveServiceMock, migrationDagValidatorMock);
  }

  @Test
  void loadsAllRowsAndDelegatesTopologicalPruningToEngine() {
    List<MigrationDagDependency> rows = rows();
    MigrationDag completeDag = new MigrationDagEngine(
      new KahnDagPlanner(),
      new ParallelLevelExecutor()
    ).buildHierarchyTree(rows);
    when(migrationDagDependencyRetrieveServiceMock.retrieve()).thenReturn(completeDag);

    MigrationDag dag = service.findDag(List.of(
      MigrationFileType.ASSESSMENTS,
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileType.ORGANIZATIONS
    ));

    assertEquals(List.of(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileType.ASSESSMENTS
    ), dag.nodes().stream().map(MigrationDagNode::fileType).toList());
    assertEquals(List.of("ROOT"), dag.nodes().getFirst().dependsOn());
    assertEquals(List.of("DEBT_POSITIONS_TYPE_ORG"), dag.nodes().get(1).dependsOn());
    assertEquals(List.of("DEBT_POSITIONS_PAID"), dag.nodes().getLast().dependsOn());
    assertEquals(List.of(MigrationFileType.ORGANIZATIONS), dag.rootNodes().stream()
      .map(MigrationDagNode::fileType)
      .toList());
    assertEquals(List.of(MigrationFileType.ASSESSMENTS), dag.childrenOf(MigrationFileType.DEBT_POSITIONS_PAID)
      .stream().map(MigrationDagNode::fileType).toList());
  }

  @Test
  void retainsNullMaximumAndDoesNotPromoteFilteredParentsToRoots() {
    List<MigrationDagDependency> rows = rows();
    MigrationDag completeDag = new MigrationDagEngine(
      new KahnDagPlanner(),
      new ParallelLevelExecutor()
    ).buildHierarchyTree(rows);
    when(migrationDagDependencyRetrieveServiceMock.retrieve()).thenReturn(completeDag);

    MigrationDag dag = service.findDag(List.of(MigrationFileType.ASSESSMENTS));

    assertEquals(1, dag.nodes().size());
    assertEquals(4, dag.nodes().getFirst().level());
    assertEquals(List.of("DEBT_POSITIONS_PAID"), dag.nodes().getFirst().dependsOn());
    assertTrue(dag.rootNodes().isEmpty());
    assertNull(dag.nodes().getFirst().maxExecutions());
  }

  @Test
  void validatesInitialDependenciesOnlyThroughExplicitInitialOperation() {
    List<MigrationDagDependency> rows = rows();
    MigrationDag completeDag = new MigrationDagEngine(
      new KahnDagPlanner(),
      new ParallelLevelExecutor()
    ).buildHierarchyTree(rows);
    when(migrationDagDependencyRetrieveServiceMock.retrieve()).thenReturn(completeDag);

    MigrationDag dag = service.findInitialDag(List.of(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.ASSESSMENTS
    ));

    assertEquals(2, dag.nodes().size());
  }

  @Test
  void rejectsNullEmptyAndNullElementFiltersWithoutReadingDatabase() {
    List<MigrationFileType> nullElementFilter = Arrays.asList(MigrationFileType.ORGANIZATIONS, null);
    assertThrows(NullPointerException.class, () -> service.findDag(null));
    assertThrows(IllegalArgumentException.class, () -> service.findDag(List.of()));
    assertThrows(NullPointerException.class, () -> service.findDag(nullElementFilter));
  }

  private List<MigrationDagDependency> rows() {
    return List.of(
      row("ROOT", "ROOT", 0),
      row("ORGANIZATIONS", "ROOT", 1),
      row("ORG_SIL_SERVICES", "ORGANIZATIONS", null),
      row("DEBT_POSITIONS_TYPE", "ROOT", 1),
      row("DEBT_POSITIONS_TYPE_ORG", "ORGANIZATIONS", 1),
      row("DEBT_POSITIONS_TYPE_ORG_OPERATORS", "DEBT_POSITIONS_TYPE_ORG", 1),
      row("DEBT_POSITIONS", "DEBT_POSITIONS_TYPE_ORG", null),
      row("DEBT_POSITIONS_PAID", "DEBT_POSITIONS_TYPE_ORG", null),
      row("PAYMENT_NOTIFICATION", "DEBT_POSITIONS_TYPE_ORG", null),
      row("PAYMENTS_REPORTING", "DEBT_POSITIONS_TYPE_ORG", null),
      row("TREASURY_CSV_COMPLETE", "DEBT_POSITIONS_TYPE_ORG", null),
      row("ASSESSMENTS_REGISTRY", "DEBT_POSITIONS_TYPE_ORG", null),
      row("ASSESSMENTS", "DEBT_POSITIONS_PAID", null)
    );
  }

  private MigrationDagDependency row(String fileType, String parent, Integer maxExecutions) {
    MigrationDagDependency row = mock(MigrationDagDependency.class);
    when(row.getFileType()).thenReturn(fileType);
    when(row.getDependsOn()).thenReturn(parent);
    if (!"ROOT".equals(fileType)) {
      when(row.getMaxExecutions()).thenReturn(maxExecutions);
    }
    return row;
  }
}
