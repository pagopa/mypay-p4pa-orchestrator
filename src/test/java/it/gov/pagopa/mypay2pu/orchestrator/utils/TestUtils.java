package it.gov.pagopa.mypay2pu.orchestrator.utils;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Assertions;
import uk.co.jemos.podam.api.*;
import uk.co.jemos.podam.common.ManufacturingContext;
import uk.co.jemos.podam.typeManufacturers.AbstractTypeManufacturer;

import java.util.*;

public class TestUtils {
  private TestUtils() {}

  static { clearDefaultTimezone();  }

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

  public static PodamFactory getPodamFactory() {
    PodamFactoryImpl podamFactory = new PodamFactoryImpl();
    podamFactory.getStrategy().addOrReplaceTypeManufacturer(
      SortedSet.class, new AbstractTypeManufacturer<>() {
        @Override
        public SortedSet<?> getType(DataProviderStrategy strategy, AttributeMetadata attributeMetadata, ManufacturingContext manufacturingCtx) {
          return new TreeSet<>();
        }
      });
    return podamFactory;
  }

  public static void assertEqualsByName(Object o1, Object o2, String... ignoredFields) {
    Assertions.assertTrue(EqualsBuilder.reflectionEquals(o1, o2, Arrays.stream(ignoredFields).toList()));
  }
}
