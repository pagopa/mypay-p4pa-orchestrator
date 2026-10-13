package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionLevel;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionNode;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Executes nodes within an ExecutionLevel in parallel using CompletableFuture and the configured TaskExecutor.
 */
@Component
public class ParallelLevelExecutor implements LevelExecutor {

  private final Executor executor;

  public ParallelLevelExecutor() {
    this.executor = null;
  }

  @Autowired(required = false)
  public ParallelLevelExecutor(TaskExecutor taskExecutor) {
    this.executor = taskExecutor != null ? taskExecutor::execute : null;
  }

  public ParallelLevelExecutor(Executor executor) {
    this.executor = executor;
  }

  @Override
  public void executeLevel(ExecutionLevel level, Consumer<ExecutionNode> nodeTask) {
    Objects.requireNonNull(level, "level must not be null");
    Objects.requireNonNull(nodeTask, "nodeTask must not be null");

    List<ExecutionNode> nodes = level.nodes();
    if (nodes.isEmpty()) {
      return;
    }

    if (nodes.size() == 1) {
      nodeTask.accept(nodes.getFirst());
      return;
    }

    CompletableFuture<?>[] futures = nodes.stream()
        .map(node -> {
          Runnable runnable = () -> nodeTask.accept(node);
          return executor != null
              ? CompletableFuture.runAsync(runnable, executor)
              : CompletableFuture.runAsync(runnable);
        })
        .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures).join();
  }
}
