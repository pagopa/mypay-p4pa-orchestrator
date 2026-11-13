package it.gov.pagopa.mypay2pu.orchestrator.utils;

import io.micrometer.tracing.CurrentTraceContext;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Utilities for propagating logging and tracing context to asynchronous tasks.
 */
public final class ThreadUtils {

  private ThreadUtils() {}

  /**
   * Captures the current MDC and tracing context for execution on another thread.
   *
   * <p>The worker's previous MDC is restored after the task completes, including when it fails.
   * The tracing context is restored by the supplied {@link CurrentTraceContext} wrapper.
   *
   * @param task task to execute with the captured context
   * @param currentTraceContext tracing context used to wrap the task
   * @return a task that runs with the captured context
   */
  public static Runnable configureThreadContext(Runnable task, CurrentTraceContext currentTraceContext) {
    Map<String, String> context = MDC.getCopyOfContextMap();
    Runnable tracedTask = currentTraceContext.wrap(task);
    return () -> {
      Map<String, String> previous = MDC.getCopyOfContextMap();
      try {
        setContext(context);
        tracedTask.run();
      } finally {
        setContext(previous);
      }
    };
  }

  private static void setContext(Map<String, String> context) {
    if (context == null) {
      MDC.clear();
    } else {
      MDC.setContextMap(context);
    }
  }
}
