package it.gov.pagopa.mypay2pu.orchestrator.utils;

import org.slf4j.MDC;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class Utilities {
  private Utilities() {}

  public static String getTraceId() {
    return MDC.get("traceId");
  }

  public static OffsetDateTime toUtcOffsetDateTime(LocalDateTime dateTime) {
    return dateTime.atOffset(ZoneOffset.UTC);
  }

  public static String removePiiFromURI(URI uri) {
    return uri != null
      ? uri.toString().replaceAll("=[^&]*", "=***")
      : null;
  }
}
