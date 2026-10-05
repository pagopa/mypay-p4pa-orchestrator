package it.gov.pagopa.mypay2pu.orchestrator.utils;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Assertions;

import java.util.*;

public class TestUtils {
  private TestUtils() {
  }

  static {
    clearDefaultTimezone();
  }

  public static void clearDefaultTimezone() {
    TimeZone.setDefault(Constants.DEFAULT_TIMEZONE);
  }

  /**
   * It will assert not null on all o's fields
   */
  public static void checkNotNullFields(Object o, String... excludedFields) {
    Set<String> excludedFieldsSet = new HashSet<>(Arrays.asList(excludedFields));
    org.springframework.util.ReflectionUtils.doWithFields(o.getClass(),
      f -> {
        f.setAccessible(true);
        Assertions.assertNotNull(f.get(o), "The field " + f.getName() + " of the input object of type " + o.getClass() + " is null!");
      },
      f -> !excludedFieldsSet.contains(f.getName()));
  }


  public static void assertEqualsByName(Object o1, Object o2, String... ignoredFields) {
    Assertions.assertTrue(EqualsBuilder.reflectionEquals(o1, o2, Arrays.stream(ignoredFields).toList()));
  }
}
