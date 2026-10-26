package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationOperation;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

@Service
public class MigrationPlanExecutorService {
  private static final MigrationFileType PLAN_ROOT = MigrationFileType.DEBT_POSITIONS_TYPE;

  static final Map<MigrationFileType, Set<MigrationFileType>> MIGRATION_PLAN = Map.ofEntries(
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

  public void execute(MigrationRequestDto request, Map<MigrationFileType, ? extends MigrationOperation> operationMap) {
    Set<MigrationFileType> selectedFileTypes = Set.copyOf(request.getFileTypesToInclude());
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      executePlan(request, PLAN_ROOT, selectedFileTypes, operationMap, executor).join();
    } catch (CompletionException exception) {
      if (exception.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      if (exception.getCause() instanceof Error cause) {
        throw cause;
      }
      throw exception;
    }
  }

  private CompletableFuture<Void> executePlan(MigrationRequestDto request,
                                              MigrationFileType migrationFileType,
                                              Set<MigrationFileType> selectedFileTypes,
                                              Map<MigrationFileType, ? extends MigrationOperation> operationMap,
                                              Executor executor) {
    if (selectedFileTypes.contains(migrationFileType)) {
      operationMap.get(migrationFileType).execute(request);
    }

    return CompletableFuture.allOf(MIGRATION_PLAN.getOrDefault(migrationFileType, Set.of()).stream()
      .map(child -> CompletableFuture.supplyAsync(
        () -> executePlan(request, child, selectedFileTypes, operationMap, executor),
        executor
      ).thenCompose(Function.identity()))
      .toArray(CompletableFuture<?>[]::new));
  }
}
