package it.gov.pagopa.mypay2pu.orchestrator.service.orchestrator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MigrationOrchestrator {
  private static final MigrationFileType PLAN_ROOT = MigrationFileType.DEBT_POSITIONS_TYPE;

  private static final Map<MigrationFileType, Set<MigrationFileType>> MIGRATION_PLAN = Map.ofEntries(
    Map.entry(PLAN_ROOT, Set.of(
      MigrationFileType.ORGANIZATIONS
    )),
    Map.entry(MigrationFileType.ORGANIZATIONS, Set.of(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.ORG_SIL_SERVICES
    )),
    Map.entry(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, Set.of(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS,
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileType.DEBT_POSITIONS_PAID,
      MigrationFileType.PAYMENT_NOTIFICATION,
      MigrationFileType.PAYMENTS_REPORTING,
      MigrationFileType.TREASURY_CSV_COMPLETE,
      MigrationFileType.ASSESSMENTS_REGISTRY
    )),
    Map.entry(MigrationFileType.DEBT_POSITIONS_PAID, Set.of(
      MigrationFileType.ASSESSMENTS
    ))
  );
  private static final Set<MigrationFileType> SUPPORTED_FILE_TYPES = supportedFileTypes();

  static {
    validatePlan();
  }

  public void execute(MigrationRequestDto request, Map<MigrationFileType, MigrationOperation> operationMap) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(operationMap, "operationMap must not be null");

    Set<MigrationFileType> selectedFileTypes = validateAndNormalizeSelection(request.getFileTypesToInclude());
    validateOperations(selectedFileTypes, operationMap);
    executePlan(request, PLAN_ROOT, selectedFileTypes, operationMap);
  }

  private Set<MigrationFileType> validateAndNormalizeSelection(Iterable<MigrationFileType> requestedFileTypes) {
    if (requestedFileTypes == null) {
      throw new IllegalArgumentException("fileTypesToInclude must be resolved before migration orchestration");
    }

    Set<MigrationFileType> selectedFileTypes = new LinkedHashSet<>();
    requestedFileTypes.forEach(fileType -> {
      if (fileType == null) {
        throw new IllegalArgumentException("fileTypesToInclude must not contain null");
      }
      if (!SUPPORTED_FILE_TYPES.contains(fileType)) {
        throw new IllegalArgumentException("Unsupported migration file type: " + fileType);
      }
      selectedFileTypes.add(fileType);
    });

    if (selectedFileTypes.isEmpty()) {
      throw new IllegalArgumentException("fileTypesToInclude must not be empty");
    }
    return Collections.unmodifiableSet(selectedFileTypes);
  }

  private void validateOperations(
    Set<MigrationFileType> selectedFileTypes,
    Map<MigrationFileType, MigrationOperation> operationMap
  ) {
    selectedFileTypes.forEach(fileType -> {
      MigrationOperation operation = operationMap.get(fileType);
      if (operation == null) {
        throw new IllegalArgumentException("No migration operation registered for selected file type " + fileType);
      }
      if (operation.migrationFileType() != fileType) {
        throw new IllegalArgumentException(
          "Migration operation registered for " + fileType
            + " reports file type " + operation.migrationFileType()
        );
      }
    });
  }

  private static Set<MigrationFileType> supportedFileTypes() {
    EnumSet<MigrationFileType> supportedFileTypes = EnumSet.noneOf(MigrationFileType.class);
    MIGRATION_PLAN.forEach((parent, children) -> {
      supportedFileTypes.add(parent);
      supportedFileTypes.addAll(children);
    });
    return Collections.unmodifiableSet(supportedFileTypes);
  }

  private static void validatePlan() {
    Map<MigrationFileType, Integer> parentCounts = new EnumMap<>(MigrationFileType.class);
    MIGRATION_PLAN.values().forEach(children ->
      children.forEach(child -> parentCounts.merge(child, 1, Integer::sum))
    );

    if (parentCounts.getOrDefault(PLAN_ROOT, 0) != 0) {
      throw new IllegalStateException("The migration plan root must not have a parent");
    }
    SUPPORTED_FILE_TYPES.stream()
      .filter(fileType -> fileType != PLAN_ROOT)
      .filter(fileType -> parentCounts.getOrDefault(fileType, 0) != 1)
      .findAny()
      .ifPresent(fileType -> {
        throw new IllegalStateException(
          "Each non-root migration file type must have exactly one parent: " + fileType
        );
      });

    Set<MigrationFileType> visited = EnumSet.noneOf(MigrationFileType.class);
    visitPlan(PLAN_ROOT, EnumSet.noneOf(MigrationFileType.class), visited);
    if (!visited.equals(SUPPORTED_FILE_TYPES) || !SUPPORTED_FILE_TYPES.equals(EnumSet.allOf(MigrationFileType.class))) {
      throw new IllegalStateException("The migration plan must cover every supported file type exactly once");
    }
  }

  private static void visitPlan(
    MigrationFileType fileType,
    Set<MigrationFileType> visiting,
    Set<MigrationFileType> visited
  ) {
    if (!visiting.add(fileType)) {
      throw new IllegalStateException("Cycle found in migration plan at " + fileType);
    }
    if (!visited.add(fileType)) {
      throw new IllegalStateException("Migration file type is reachable through multiple paths: " + fileType);
    }

    MIGRATION_PLAN.getOrDefault(fileType, Set.of()).forEach(child -> visitPlan(child, visiting, visited));
    visiting.remove(fileType);
  }

  private void executePlan(
    MigrationRequestDto request,
    MigrationFileType migrationFileType,
    Set<MigrationFileType> selectedFileTypes,
    Map<MigrationFileType, MigrationOperation> operationMap
  ) {
    if (selectedFileTypes.contains(migrationFileType)) {
      operationMap.get(migrationFileType).execute(request);
    }

    MIGRATION_PLAN.getOrDefault(migrationFileType, Set.of()).parallelStream().forEach(child ->
      executePlan(request, child, selectedFileTypes, operationMap)
    );
  }
}
