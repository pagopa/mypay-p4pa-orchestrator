package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MigrationPlanExecutorServiceTest {

  @InjectMocks
  private MigrationPlanExecutorService planExecutor;

  @Test
  void givenMigrationPlanWhenValidateThenVisitEveryFileTypeOnceWithoutCycles() {
    Set<MigrationFileType> visited = EnumSet.noneOf(MigrationFileType.class);

    visitPlan(MigrationFileType.DEBT_POSITIONS_TYPE, visited);

    assertEquals(EnumSet.allOf(MigrationFileType.class), visited);
  }

  @Test
  void givenSelectedDescendantWhenExecuteThenSkipUnselectedAncestors() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();

    planExecutor.execute(request(MigrationFileType.ASSESSMENTS), operations(executed));

    assertEquals(Set.of(MigrationFileType.ASSESSMENTS), executed);
  }

  @Test
  void givenFailureOnSelectedParentWhenExecuteThenSkipItsDescendants() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
    MigrationFileType failedParent = MigrationFileType.DEBT_POSITIONS_TYPE_ORG;
    IllegalStateException failure = new IllegalStateException("parent failed");
    Map<MigrationFileType, MigrationOperation> operations = operations(executed);
    operations.put(failedParent, operation(failedParent, executed, failure));

    IllegalStateException thrown = assertThrows(
      IllegalStateException.class,
      () -> planExecutor.execute(request(
        MigrationFileType.DEBT_POSITIONS_TYPE,
        MigrationFileType.ORGANIZATIONS,
        failedParent,
        MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS
      ), operations)
    );

    assertEquals(failure, thrown);
    assertEquals(Set.of(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS,
      failedParent
    ), executed);
  }

  private void visitPlan(MigrationFileType fileType, Set<MigrationFileType> visited) {
    assertTrue(visited.add(fileType), "Cycle or repeated file type in migration plan: " + fileType);
    MigrationPlanExecutorService.MIGRATION_PLAN.getOrDefault(fileType, Set.of())
      .forEach(child -> visitPlan(child, visited));
  }

  private Map<MigrationFileType, MigrationOperation> operations(Set<MigrationFileType> executed) {
    Map<MigrationFileType, MigrationOperation> operations = new EnumMap<>(MigrationFileType.class);
    for (MigrationFileType fileType : MigrationFileType.values()) {
      operations.put(fileType, operation(fileType, executed, null));
    }
    return operations;
  }

  private MigrationOperation operation(MigrationFileType fileType,
                                       Set<MigrationFileType> executed,
                                       RuntimeException failure) {
    return new MigrationOperation() {
      @Override
      public MigrationFileType getMigrationFileType() {
        return fileType;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        executed.add(fileType);
        if (failure != null) {
          throw failure;
        }
      }
    };
  }

  private MigrationRequestDto request(MigrationFileType... fileTypes) {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setFileTypesToInclude(List.of(fileTypes));
    return request;
  }
}
