package it.gov.pagopa.mypay2pu.orchestrator.utils;

import java.time.ZoneId;
import java.util.TimeZone;

public class Constants {

  private Constants(){}

  public static final ZoneId ZONEID = ZoneId.of("Europe/Rome");
  public static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone(ZONEID);

}

