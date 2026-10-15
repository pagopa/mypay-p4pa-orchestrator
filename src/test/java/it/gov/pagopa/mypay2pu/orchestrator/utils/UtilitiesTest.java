package it.gov.pagopa.mypay2pu.orchestrator.utils;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.URI;

public class UtilitiesTest {

  public static void setTraceId(String traceId) {
    MDC.put("traceId", traceId);
  }
  public static void clearTraceIdContext(){
    MDC.clear();
  }

  @Test
  void testGetTraceId(){
    // Given
    String expectedResult = "TRACEID";
    setTraceId(expectedResult);

    // When
    String result = Utilities.getTraceId();

    // Then
    Assertions.assertSame(expectedResult, result);
    clearTraceIdContext();
  }

  @Test
  void givenUriWhenRemovePiiFromURIThenOk(){
    String result = Utilities.removePiiFromURI(URI.create("https://host/path?param1=PII&param2=noPII"));
    Assertions.assertEquals("https://host/path?param1=***&param2=***", result);
  }

  @Test
  void givenNullUriWhenRemovePiiFromURIThenOk(){
    Assertions.assertNull(Utilities.removePiiFromURI(null));
  }
}
