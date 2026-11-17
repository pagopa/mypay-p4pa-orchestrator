package it.gov.pagopa.mypay2pu.orchestrator.utils;

import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
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

  public static Integer toNonNegativeInteger(Long value, String fieldName) {
    if (value == null) {
      return null;
    }
    int intValue;
    try {
      intValue = Math.toIntExact(value);
    } catch (ArithmeticException exception) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "%s exceeds the supported range".formatted(fieldName)
      );
    }
    if (intValue < 0) {
      throw new InvalidValueException(
        "TRANSFER_INVALID_UPLOAD_DETAILS",
        "%s must not be negative".formatted(fieldName)
      );
    }
    return intValue;
  }
}
