package it.gov.pagopa.mypay2pu.orchestrator.service.orchestrator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationOrchestratorTest {

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

  private final MigrationOrchestrator orchestrator = new MigrationOrchestrator();

  @Test
  void visitsAllTwelveFileTypesAndExecutesDuplicatesOnlyOnce() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();

    orchestrator.execute(
      request(MigrationFileType.values()),
      recordingOperations(executed, null)
    );

    assertEquals(EnumSet.allOf(MigrationFileType.class), executed);
  }

  @Test
  void executesSelectedTypesOnceAndVisitsExcludedAncestors() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
    Map<MigrationFileType, MigrationOperation> operations = recordingOperations(executed, null);

    orchestrator.execute(request(
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

    orchestrator.execute(
      request(MigrationFileType.ASSESSMENTS_REGISTRY),
      recordingOperations(executed, null)
    );

    assertEquals(Set.of(MigrationFileType.ASSESSMENTS_REGISTRY), executed);
  }

  @Test
  void validatesEverySelectedOperationBeforeExecutingAny() {
    List<MigrationFileType> executed = new ArrayList<>();
    Map<MigrationFileType, MigrationOperation> operations = new EnumMap<>(MigrationFileType.class);
    operations.put(
      MigrationFileType.DEBT_POSITIONS_TYPE,
      operation(MigrationFileType.DEBT_POSITIONS_TYPE, executed, null)
    );

    MigrationRequestDto request = request(MigrationFileType.DEBT_POSITIONS_TYPE, MigrationFileType.ORGANIZATIONS);
    assertThrows(IllegalArgumentException.class, () -> orchestrator.execute(
      request,
      operations
    ));

    assertEquals(List.of(), executed);
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

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> orchestrator.execute(
      request,
      operations
    ));

    assertEquals(failure, thrown);
    assertEquals(List.of(MigrationFileType.DEBT_POSITIONS_TYPE), executed);
  }

  @Test
  void rejectsUnresolvedOrEmptySelection() {
    Map<MigrationFileType, MigrationOperation> migrationFileTypeMigrationOperationMap = recordingOperations(ConcurrentHashMap.newKeySet(), null);
    MigrationRequestDto request = request();
    assertThrows(IllegalArgumentException.class, () -> orchestrator.execute(
      request,
      migrationFileTypeMigrationOperationMap
    ));

    MigrationRequestDto unresolvedRequest = new MigrationRequestDto();
    unresolvedRequest.setFileTypesToInclude(null);
    assertThrows(IllegalArgumentException.class, () -> orchestrator.execute(
      unresolvedRequest,
      migrationFileTypeMigrationOperationMap
    ));
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
