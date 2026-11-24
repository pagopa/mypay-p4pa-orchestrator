package it.gov.pagopa.mypay2pu.orchestrator.service;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.AsyncConfiguration;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferOutcomeException;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationPlanExecutorServiceTest {

  private final TransferOutcomeCollector transferOutcomeCollectorMock = Mockito.mock(TransferOutcomeCollector.class);
  private final MigrationPlanExecutorService planExecutor = new MigrationPlanExecutorService(Tracer.NOOP, transferOutcomeCollectorMock);

  @AfterEach
  void verifyMocks() {
    Mockito.verifyNoMoreInteractions(transferOutcomeCollectorMock);
  }

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
  void givenDeltaRequestWhenExecuteThenTraversesUnselectedAncestorsWithOriginalContract() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
    MigrationRequestDto request = request(MigrationFileType.DEBT_POSITIONS).orchestrationMode(OrchestrationMode.DELTA);
    MigrationOperation operation = new MigrationOperation() {
      @Override
      public MigrationFileType getMigrationFileType() {
        return MigrationFileType.DEBT_POSITIONS;
      }

      @Override
      public void execute(MigrationRequestDto actual) {
        assertSame(request, actual);
        executed.add(getMigrationFileType());
      }
    };

    planExecutor.execute(request, Map.of(MigrationFileType.DEBT_POSITIONS, operation));

    assertEquals(Set.of(MigrationFileType.DEBT_POSITIONS), executed);
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
    MigrationRequestDto request = request(
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

  @Test
  void givenSelectedDescendantThrowsErrorWhenExecuteThenRethrowsError() {
    AssertionError failure = new AssertionError("operation failed");
    MigrationOperation operation = operation(MigrationFileType.ORGANIZATIONS, request -> {
      throw failure;
    });
    MigrationRequestDto migrationRequest = request(MigrationFileType.ORGANIZATIONS);
    Map<MigrationFileType, MigrationOperation> operationMap = Map.of(
      MigrationFileType.ORGANIZATIONS, operation
    );

    AssertionError thrown = assertThrows(
      AssertionError.class,
      () -> planExecutor.execute(migrationRequest, operationMap)
    );

    assertSame(failure, thrown);
  }

  @Test
  void givenCompletionExceptionWithCheckedCauseWhenExecuteThenRethrowsCompletionException() {
    CompletionException failure = new CompletionException(new IOException("checked failure"));
    MigrationOperation operation = operation(MigrationFileType.DEBT_POSITIONS_TYPE, request -> {
      throw failure;
    });
    MigrationRequestDto migrationRequest = request(MigrationFileType.DEBT_POSITIONS_TYPE);
    Map<MigrationFileType, MigrationOperation> operationMap = Map.of(
      MigrationFileType.DEBT_POSITIONS_TYPE, operation
    );

    CompletionException thrown = assertThrows(
      CompletionException.class,
      () -> planExecutor.execute(migrationRequest, operationMap)
    );

    assertSame(failure, thrown);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void givenTraceWhenExecuteAsyncPlanThenPreservesItAcrossClientScopesAndRestoresWorker(boolean fail)
    throws Exception {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    try (SdkTracerProvider provider = SdkTracerProvider.builder().build()) {
      var otelTracer = provider.get("migration-test");
      Slf4JEventListener listener = new Slf4JEventListener();
      Tracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), listener::onEvent);
      ContextStorage storage = new EventPublishingContextWrapper(listener::onEvent).apply(ContextStorage.get());
      MigrationPlanExecutorService tracedPlanExecutor = new MigrationPlanExecutorService(tracer, transferOutcomeCollectorMock);
      ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfiguration()
        .orchestratorTaskExecutor(1, 1, 10, tracer);
      executor.initialize();
      var parent = tracer.nextSpan().name("migration").start();
      try {
        Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
        IllegalStateException failure = new IllegalStateException("client failed");
        try (var ignored = tracer.withSpan(parent)) {
          Map<String, String> parentMdc = Map.of(
            "traceId", parent.context().traceId(),
            "spanId", parent.context().spanId(),
            "externalUserId", "migration-operator"
          );
          MDC.setContextMap(parentMdc);
          Map<MigrationFileType, MigrationOperation> operations = new EnumMap<>(MigrationFileType.class);
          for (MigrationFileType fileType : List.of(
            MigrationFileType.DEBT_POSITIONS_TYPE,
            MigrationFileType.ORGANIZATIONS,
            MigrationFileType.DEBT_POSITIONS_TYPE_ORG
          )) {
            operations.put(fileType, operation(fileType, request -> {
              assertEquals(parent.context().traceId(), tracer.currentSpan().context().traceId());
              assertEquals(parent.context().spanId(), tracer.currentSpan().context().spanId());
              assertEquals(parentMdc, MDC.getCopyOfContextMap());
              assertEquals(fileType != MigrationFileType.DEBT_POSITIONS_TYPE, Thread.currentThread().isVirtual());
              var clientSpan = otelTracer.spanBuilder("extractor-http").startSpan();
              try {
                try (var scope = storage.attach(Context.current().with(clientSpan))) {
                  assertEquals(parent.context().traceId(), clientSpan.getSpanContext().getTraceId());
                  assertEquals(clientSpan.getSpanContext().getSpanId(), MDC.get("spanId"));
                }
                assertEquals(parentMdc, MDC.getCopyOfContextMap());
                assertEquals(parent.context().spanId(), tracer.currentSpan().context().spanId());
                executed.add(fileType);
                if (fail && fileType == MigrationFileType.DEBT_POSITIONS_TYPE_ORG) {
                  throw failure;
                }
              } finally {
                clientSpan.end();
              }
            }));
          }
          var result = executor.submit(() -> tracedPlanExecutor.execute(
            request(operations.keySet().toArray(MigrationFileType[]::new)), operations));
          if (fail) {
            assertSame(failure, assertThrows(ExecutionException.class,
              () -> result.get(10, TimeUnit.SECONDS)).getCause());
          } else {
            result.get(10, TimeUnit.SECONDS);
          }
          assertEquals(operations.keySet(), executed);
          assertEquals(parentMdc, MDC.getCopyOfContextMap());
        }
        MDC.clear();
        executor.submit(() -> {
          assertNull(tracer.currentSpan());
          assertNull(MDC.getCopyOfContextMap());
        }).get(10, TimeUnit.SECONDS);
      } finally {
        parent.end();
        executor.shutdown();
      }
    } finally {
      if (previous == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(previous);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(value = MigrationAction.class, names = {"TRANSFER", "COMPLETE"})
  void givenParentTransferOutcomeWhenExecuteThenAwaitsItBeforeStartingChild(MigrationAction action) {
    UUID migrationId = UUID.randomUUID();
    AtomicBoolean transferOutcomeAvailable = new AtomicBoolean();
    Mockito.doAnswer(invocation -> {
      transferOutcomeAvailable.set(true);
      return null;
    }).when(transferOutcomeCollectorMock).collect(migrationId, MigrationFileType.ORGANIZATIONS);
    MigrationOperation parent = operation(MigrationFileType.ORGANIZATIONS,
      request -> assertFalse(transferOutcomeAvailable.get()));
    MigrationOperation child = operation(MigrationFileType.ORG_SIL_SERVICES,
      request -> assertTrue(transferOutcomeAvailable.get()));

    planExecutor.execute(
      request(MigrationFileType.ORGANIZATIONS, MigrationFileType.ORG_SIL_SERVICES)
        .migrationId(migrationId).action(action),
      Map.of(MigrationFileType.ORGANIZATIONS, parent, MigrationFileType.ORG_SIL_SERVICES, child));

    Mockito.verify(transferOutcomeCollectorMock).collect(migrationId, MigrationFileType.ORGANIZATIONS);
    Mockito.verify(transferOutcomeCollectorMock).collect(migrationId, MigrationFileType.ORG_SIL_SERVICES);
  }

  @Test
  void givenFailedParentTransferOutcomeWhenExecuteThenBlocksOnlyItsDescendants() {
    UUID migrationId = UUID.randomUUID();
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();
    TransferOutcomeException failure = new TransferOutcomeException("TRANSFER_OUTCOME_PU_FAILED", "parent failed");
    Mockito.doThrow(failure).when(transferOutcomeCollectorMock).collect(migrationId, MigrationFileType.DEBT_POSITIONS_TYPE_ORG);
    Map<MigrationFileType, MigrationOperation> operations = operations(executed,
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.DEBT_POSITIONS,
      MigrationFileType.ORG_SIL_SERVICES);
    MigrationRequestDto request = request(operations.keySet().toArray(MigrationFileType[]::new))
      .migrationId(migrationId).action(MigrationAction.TRANSFER);

    TransferOutcomeException thrown = assertThrows(TransferOutcomeException.class, () -> planExecutor.execute(request, operations));

    assertSame(failure, thrown);
    assertEquals(Set.of(MigrationFileType.ORGANIZATIONS, MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.ORG_SIL_SERVICES), executed);
    Mockito.verify(transferOutcomeCollectorMock).collect(migrationId, MigrationFileType.ORGANIZATIONS);
    Mockito.verify(transferOutcomeCollectorMock).collect(migrationId, MigrationFileType.DEBT_POSITIONS_TYPE_ORG);
    Mockito.verify(transferOutcomeCollectorMock).collect(migrationId, MigrationFileType.ORG_SIL_SERVICES);
  }

  @Test
  void givenExportActionWhenExecuteThenNeverCollectsPuTransferOutcome() {
    Set<MigrationFileType> executed = ConcurrentHashMap.newKeySet();

    planExecutor.execute(
      request(MigrationFileType.ORGANIZATIONS).action(MigrationAction.EXPORT),
      operations(executed, MigrationFileType.ORGANIZATIONS));

    assertEquals(Set.of(MigrationFileType.ORGANIZATIONS), executed);
    Mockito.verifyNoInteractions(transferOutcomeCollectorMock);
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

  private MigrationRequestDto request(MigrationFileType... fileTypes) {
    return new MigrationRequestDto().fileTypesToInclude(List.of(fileTypes));
  }
}
