package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class MigrationPlanExecutorServiceTest {

  private static final Map<MigrationFileType, MigrationFileType> PARENTS = Map.ofEntries(
    Map.entry(MigrationFileType.ORGANIZATIONS, MigrationFileType.DEBT_POSITIONS_TYPE),
    Map.entry(MigrationFileType.ORG_SIL_SERVICES, MigrationFileType.ORGANIZATIONS),
    Map.entry(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, MigrationFileType.ORGANIZATIONS),
    Map.entry(MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS, MigrationFileType.DEBT_POSITIONS_TYPE_ORG),
    Map.entry(MigrationFileType.DEBT_POSITIONS, MigrationFileType.DEBT_POSITIONS_TYPE_ORG),
    Map.entry(MigrationFileType.DEBT_POSITIONS_PAID, MigrationFileType.DEBT_POSITIONS_TYPE_ORG),
    Map.entry(MigrationFileType.ASSESSMENTS, MigrationFileType.DEBT_POSITIONS_PAID),
    Map.entry(MigrationFileType.PAYMENT_NOTIFICATION, MigrationFileType.DEBT_POSITIONS_TYPE_ORG),
    Map.entry(MigrationFileType.PAYMENTS_REPORTING, MigrationFileType.DEBT_POSITIONS_TYPE_ORG),
    Map.entry(MigrationFileType.TREASURY_CSV_COMPLETE, MigrationFileType.DEBT_POSITIONS_TYPE_ORG),
    Map.entry(MigrationFileType.ASSESSMENTS_REGISTRY, MigrationFileType.DEBT_POSITIONS_TYPE_ORG)
  );

  private final MigrationPlanExecutorService planExecutor = new MigrationPlanExecutorService();

  @Test
  void validatePlan() {
    Set<MigrationFileType> visited = EnumSet.noneOf(MigrationFileType.class);
    visitPlan(MigrationFileType.DEBT_POSITIONS_TYPE, visited);

    assertEquals(EnumSet.allOf(MigrationFileType.class), visited);
    MigrationPlanExecutorService.MIGRATION_PLAN.forEach((parent, children) ->
      children.forEach(child -> assertEquals(parent, PARENTS.get(child)))
    );
  }

  private void visitPlan(MigrationFileType fileType, Set<MigrationFileType> visited) {
    assertTrue(visited.add(fileType), "Cycle or multiple paths in migration plan at " + fileType);
    MigrationPlanExecutorService.MIGRATION_PLAN.getOrDefault(fileType, Set.of())
      .forEach(child -> visitPlan(child, visited));
  }

  @Test
  void visitsAllFileTypesOnVirtualThreads() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();

    planExecutor.execute(
      request(MigrationFileType.values()),
      recordingOperations(executed, null)
    );

    assertEquals(EnumSet.allOf(MigrationFileType.class), executed);
  }

  @Test
  void executesSelectedTypesOnceAndVisitsExcludedAncestors() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
    Map<MigrationFileType, MigrationOperation> operations = recordingOperations(executed, null);

    planExecutor.execute(request(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ASSESSMENTS,
      MigrationFileType.ORGANIZATIONS
    ), operations);

    assertEquals(Set.of(
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ASSESSMENTS
    ), executed);
  }

  @Test
  void completesLeafNodesWithoutChildren() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();

    planExecutor.execute(
      request(MigrationFileType.ASSESSMENTS_REGISTRY),
      recordingOperations(executed, null)
    );

    assertEquals(Set.of(MigrationFileType.ASSESSMENTS_REGISTRY), executed);
  }

  @Test
  void runsSiblingBranchesConcurrentlyWithoutAGlobalLevelBarrier() {
    CountDownLatch slowSiblingStarted = new CountDownLatch(1);
    CountDownLatch descendantCompleted = new CountDownLatch(1);
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
    Map<MigrationFileType, MigrationOperation> operations = recordingOperations(executed, null);
    operations.put(MigrationFileType.ORG_SIL_SERVICES, new MigrationOperation() {
      @Override
      public MigrationFileType migrationFileType() {
        return MigrationFileType.ORG_SIL_SERVICES;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        slowSiblingStarted.countDown();
        await(descendantCompleted);
        executed.add(migrationFileType());
      }
    });
    operations.put(MigrationFileType.ASSESSMENTS, new MigrationOperation() {
      @Override
      public MigrationFileType migrationFileType() {
        return MigrationFileType.ASSESSMENTS;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        await(slowSiblingStarted);
        executed.add(migrationFileType());
        descendantCompleted.countDown();
      }
    });

    planExecutor.execute(request(MigrationFileType.values()), operations);

    assertEquals(EnumSet.allOf(MigrationFileType.class), executed);
  }

  @Test
  void doesNotExecuteChildrenWhenTheirSelectedParentFails() {
    List<MigrationFileType> executed = new ArrayList<>();
    IllegalStateException failure = new IllegalStateException("parent failed");
    Map<MigrationFileType, MigrationOperation> operations = new EnumMap<>(MigrationFileType.class);
    MigrationRequestDto request = request(MigrationFileType.DEBT_POSITIONS_TYPE, MigrationFileType.ORGANIZATIONS);
    operations.put(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      operation(MigrationFileType.DEBT_POSITIONS_TYPE, executed, failure)
    );
    operations.put(
      MigrationFileType.ORGANIZATIONS,
      operation(MigrationFileType.ORGANIZATIONS, executed, null)
    );

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> planExecutor.execute(
      request,
      operations
    ));

    assertEquals(failure, thrown);
    assertEquals(List.of(MigrationFileType.DEBT_POSITIONS_TYPE), executed);
  }

  @Test
  void propagatesFailureFromANestedBranchWithoutExecutingItsDescendants() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
    Map<MigrationFileType, MigrationOperation> operations = recordingOperations(executed, null);
    operations.put(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, new MigrationOperation() {

      @Override
      public MigrationFileType migrationFileType() {
        return MigrationFileType.DEBT_POSITIONS_TYPE_ORG;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        executed.add(migrationFileType());
        throw new IllegalStateException("nested parent failed");
      }
    });

    MigrationRequestDto request = request(MigrationFileType.values());
    assertThrows(IllegalStateException.class,
      () -> planExecutor.execute(request, operations),
      "nested parent failed"
    );

    assertEquals(Set.of(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.ORG_SIL_SERVICES
    ), executed);
  }

  private void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for a sibling branch");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for a sibling branch", exception);
    }
  }

  private Map<MigrationFileType, MigrationOperation> recordingOperations(
    Set<MigrationFileType> executed,
    RuntimeException failure
  ) {
    Map<MigrationFileType, MigrationOperation> operations = new EnumMap<>(MigrationFileType.class);
    for (MigrationFileType fileType : MigrationFileType.values()) {
      operations.put(fileType, new MigrationOperation() {
        @Override
        public MigrationFileType migrationFileType() {
          return fileType;
        }

        @Override
        public void execute(MigrationRequestDto request) {
          assertTrue(Thread.currentThread().isVirtual());
          MigrationFileType parent = PARENTS.get(fileType);
          if (parent != null
            && request.getFileTypesToInclude().contains(parent)
            && !executed.contains(parent)) {
            throw new IllegalStateException("Parent must complete before selected child: " + fileType);
          }
          executed.add(fileType);
          if (failure != null && fileType == MigrationFileType.DEBT_POSITIONS_TYPE) {
            throw failure;
          }
        }
      });
    }
    return operations;
  }

  private MigrationOperation operation(
    MigrationFileType fileType,
    List<MigrationFileType> executed,
    RuntimeException failure
  ) {
    return new MigrationOperation() {
      @Override
      public MigrationFileType migrationFileType() {
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
