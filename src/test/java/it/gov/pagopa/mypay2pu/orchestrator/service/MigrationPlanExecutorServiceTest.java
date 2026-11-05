package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.MigrationRequestWithMigrationExecutionModelDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationOperation;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationPlanExecutorServiceTest {

  private final MigrationPlanExecutorService planExecutor = new MigrationPlanExecutorService();

  @Test
  void givenMigrationPlanWhenValidateThenVisitEveryFileTypeOnceWithoutCycles() {
    Set<MigrationFileType> visited = EnumSet.noneOf(MigrationFileType.class);

    visitPlan(MigrationFileType.DEBT_POSITIONS_TYPE, visited);

    assertEquals(EnumSet.allOf(MigrationFileType.class), visited);
  }

  @Test
  void givenSelectedDescendantWhenExecuteThenSkipUnselectedAncestors() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();

    planExecutor.execute(
      request(MigrationFileType.ASSESSMENTS),
      operations(executed, MigrationFileType.ASSESSMENTS)
    );

    assertEquals(Set.of(MigrationFileType.ASSESSMENTS), executed);
  }

  @Test
  void givenSelectedRootWhenExecuteThenRunsOperationOnCallingThread() {
    Thread callingThread = Thread.currentThread();
    MigrationOperation operation = operation(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      request -> assertSame(callingThread, Thread.currentThread())
    );

    planExecutor.execute(request(MigrationFileType.DEBT_POSITIONS_TYPE), Map.of(
      MigrationFileType.DEBT_POSITIONS_TYPE, operation
    ));
  }

  @Test
  void givenFailureOnSelectedParentWhenExecuteThenSkipItsDescendants() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
    MigrationFileType failedParent = MigrationFileType.DEBT_POSITIONS_TYPE_ORG;
    IllegalStateException failure = new IllegalStateException("parent failed");
    Map<MigrationFileType, MigrationOperation> operations = operations(
      executed,
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS,
      failedParent,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS
    );
    operations.put(failedParent, operation(failedParent, request -> {
      executed.add(failedParent);
      throw failure;
    }));
    MigrationRequestWithMigrationExecutionModelDto request = request(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS,
      failedParent,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS
    );

    IllegalStateException thrown = assertThrows(
      IllegalStateException.class,
      () -> planExecutor.execute(request, operations)
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

  private Map<MigrationFileType, MigrationOperation> operations(Set<MigrationFileType> executed,
                                                                MigrationFileType... fileTypes) {
    Map<MigrationFileType, MigrationOperation> operations = new EnumMap<>(MigrationFileType.class);
    for (MigrationFileType fileType : fileTypes) {
      operations.put(fileType, operation(fileType, request -> executed.add(fileType)));
    }
    return operations;
  }

  private MigrationOperation operation(MigrationFileType fileType, Consumer<MigrationRequestDto> action) {
    return new MigrationOperation() {
      @Override
      public MigrationFileType getMigrationFileType() {
        return fileType;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        action.accept(request);
      }
    };
  }

  private MigrationRequestWithMigrationExecutionModelDto request(MigrationFileType... fileTypes) {
    MigrationRequestWithMigrationExecutionModelDto request = new MigrationRequestWithMigrationExecutionModelDto();
    request.setFileTypesToInclude(List.of(fileTypes));
    return request;
  }
}
