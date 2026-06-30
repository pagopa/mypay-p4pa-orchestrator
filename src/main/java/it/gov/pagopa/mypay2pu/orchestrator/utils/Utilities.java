package it.gov.pagopa.mypay2pu.orchestrator.utils;

import org.slf4j.MDC;

import java.net.URI;

public class Utilities {
  private Utilities() {}

  public static String getTraceId() {
    return MDC.get("traceId");
  }

  public static String removePiiFromURI(URI uri) {
    return uri != null
      ? uri.toString().replaceAll("=[^&]*", "=***")
      : null;
  }
}
