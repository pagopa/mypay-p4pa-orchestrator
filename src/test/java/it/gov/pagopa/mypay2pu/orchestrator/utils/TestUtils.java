package it.gov.pagopa.mypay2pu.orchestrator.utils;

import java.util.TimeZone;

public class TestUtils {

  static {
    clearDefaultTimezone();
  }

  public static void clearDefaultTimezone() {
    TimeZone.setDefault(Constants.DEFAULT_TIMEZONE);
  }
}
