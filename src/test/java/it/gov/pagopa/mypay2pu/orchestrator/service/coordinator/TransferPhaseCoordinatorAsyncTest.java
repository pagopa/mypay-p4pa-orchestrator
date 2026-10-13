package it.gov.pagopa.mypay2pu.orchestrator.service.coordinator;

import it.gov.pagopa.mypay2pu.orchestrator.config.AsyncConfiguration;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(classes = {AsyncConfiguration.class, TransferPhaseCoordinator.class})
@TestPropertySource(properties = {
  "orchestrator.async.core-pool-size=1",
  "orchestrator.async.max-pool-size=1",
  "orchestrator.async.queue-capacity=1"
})
class TransferPhaseCoordinatorAsyncTest {

  @Autowired
  private TransferPhaseCoordinator transferPhaseCoordinator;

  @Autowired
  @Qualifier("orchestratorTaskExecutor")
  private Executor orchestratorTaskExecutor;

  @Test
  void startReturnsBeforeTheCoordinatorRuns() throws Exception {
    CountDownLatch executorTaskStarted = new CountDownLatch(1);
    CountDownLatch releaseExecutorTask = new CountDownLatch(1);
    orchestratorTaskExecutor.execute(() -> {
      executorTaskStarted.countDown();
      try {
        releaseExecutorTask.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    });

    try {
      assertTrue(executorTaskStarted.await(5, TimeUnit.SECONDS));

      MigrationExecution execution = MigrationExecution.builder()
        .id(UUID.randomUUID())
        .build();
      CompletableFuture<Void> start = transferPhaseCoordinator.start(execution);

      assertFalse(start.isDone());
      releaseExecutorTask.countDown();
      assertNull(start.get(5, TimeUnit.SECONDS));
    } finally {
      releaseExecutorTask.countDown();
    }
  }
}
