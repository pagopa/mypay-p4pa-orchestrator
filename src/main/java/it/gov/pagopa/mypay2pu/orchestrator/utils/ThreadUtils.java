package it.gov.pagopa.mypay2pu.orchestrator.utils;

import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

public final class ThreadUtils {

  private ThreadUtils() {}

  public static Runnable configureThreadContext(Runnable task) {
    Map<String, String> context = MDC.getCopyOfContextMap();
    return () -> {
      Map<String, String> previous = MDC.getCopyOfContextMap();
      try {
        setContext(context);
        String traceId = MDC.get("traceId");
        if (!StringUtils.hasText(traceId)) {
          MDC.put("traceId", UUID.randomUUID().toString().replace("-", ""));
        }
        task.run();
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
