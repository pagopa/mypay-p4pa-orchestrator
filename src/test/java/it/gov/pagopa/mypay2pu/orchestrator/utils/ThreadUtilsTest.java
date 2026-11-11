package it.gov.pagopa.mypay2pu.orchestrator.utils;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreadUtilsTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void givenCapturedMdcWhenConfigureThreadContextThenRestoresPreviousContext(boolean fail) {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    try {
      MDC.setContextMap(Map.of("traceId", "parent"));
      IllegalStateException failure = new IllegalStateException("task failed");
      Runnable task = ThreadUtils.configureThreadContext(() -> {
        assertEquals(Map.of("traceId", "parent"), MDC.getCopyOfContextMap());
        MDC.put("taskOnly", "value");
        if (fail) {
          throw failure;
        }
      });
      MDC.setContextMap(Map.of("traceId", "worker"));
      if (fail) {
        assertSame(failure, assertThrows(IllegalStateException.class, task::run));
      } else {
        task.run();
      }
      assertEquals(Map.of("traceId", "worker"), MDC.getCopyOfContextMap());

      MDC.clear();
      Runnable noContextTask = ThreadUtils.configureThreadContext(
        () -> assertNull(MDC.getCopyOfContextMap()));
      MDC.put("traceId", "worker");
      noContextTask.run();
      assertEquals(Map.of("traceId", "worker"), MDC.getCopyOfContextMap());
    } finally {
      if (previous == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(previous);
      }
    }
  }
}
