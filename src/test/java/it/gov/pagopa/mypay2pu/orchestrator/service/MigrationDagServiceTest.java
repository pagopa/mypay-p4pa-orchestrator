package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagNode;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationDagDependencyRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MigrationDagServiceTest {
  private final MigrationDagDependencyRepository repository = mock(MigrationDagDependencyRepository.class);
  private final MigrationDagService service = new MigrationDagService(repository, new MigrationDagEngine());

  @Test
  void loadsAllRowsAndDelegatesTopologicalPruningToEngine() {
    when(repository.findAll()).thenReturn(rows());

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
    verify(repository).findAll();
  }

  @Test
  void retainsNullMaximumAndDoesNotPromoteFilteredParentsToRoots() {
    when(repository.findAll()).thenReturn(rows());

    MigrationDag dag = service.findDag(List.of(MigrationFileType.ASSESSMENTS));

    assertEquals(1, dag.nodes().size());
    assertEquals(4, dag.nodes().getFirst().level());
    assertEquals(List.of("DEBT_POSITIONS_PAID"), dag.nodes().getFirst().dependsOn());
    assertTrue(dag.rootNodes().isEmpty());
    assertNull(dag.nodes().getFirst().maxExecutions());
  }

  @Test
  void rejectsNullEmptyAndNullElementFiltersWithoutReadingDatabase() {
    assertThrows(NullPointerException.class, () -> service.findDag(null));
    assertThrows(IllegalArgumentException.class, () -> service.findDag(List.of()));
    assertThrows(NullPointerException.class, () -> service.findDag(Arrays.asList(
      MigrationFileType.ORGANIZATIONS, null
    )));
    verifyNoInteractions(repository);
  }

  private List<MigrationDagDependency> rows() {
    return List.of(
      row(1, "ROOT", "ROOT", 0),
      row(2, "ORGANIZATIONS", "ROOT", 1),
      row(3, "DEBT_POSITIONS_TYPE", "ROOT", 1),
      row(4, "DEBT_POSITIONS_TYPE_ORG", "ORGANIZATIONS", 1),
      row(5, "DEBT_POSITIONS", "DEBT_POSITIONS_TYPE_ORG", null),
      row(6, "DEBT_POSITIONS_PAID", "DEBT_POSITIONS_TYPE_ORG", null),
      row(7, "ASSESSMENTS", "DEBT_POSITIONS_PAID", null)
    );
  }

  private MigrationDagDependency row(Integer id, String fileType, String parent, Integer maxExecutions) {
    MigrationDagDependency row = mock(MigrationDagDependency.class);
    org.mockito.Mockito.when(row.getId()).thenReturn(id);
    org.mockito.Mockito.when(row.getFileType()).thenReturn(fileType);
    org.mockito.Mockito.when(row.getDependsOn()).thenReturn(parent);
    org.mockito.Mockito.when(row.getMaxExecutions()).thenReturn(maxExecutions);
    return row;
  }
}
