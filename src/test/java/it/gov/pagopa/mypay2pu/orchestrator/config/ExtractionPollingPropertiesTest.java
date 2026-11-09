package it.gov.pagopa.mypay2pu.orchestrator.config;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionPollingPropertiesTest {

  @ParameterizedTest
  @MethodSource("validDurations")
  void givenValidDurationsWhenConstructThenRetainsValues(Duration interval, Duration timeout) {
    ExtractionPollingProperties properties = new ExtractionPollingProperties(interval, timeout, null);

    assertEquals(interval, properties.statusPollInterval());
    assertEquals(timeout, properties.statusTimeout());
  }

  @ParameterizedTest
  @MethodSource("invalidDurations")
  void givenInvalidDurationsWhenConstructThenRejectsConfiguration(Duration interval, Duration timeout, String message) {
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class, () -> new ExtractionPollingProperties(interval, timeout, null)
    );

    assertEquals(message, exception.getMessage());
  }

  @Test
  void givenInvalidPollingIntervalWhenRefreshThenFailsAtStartup() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "9s",
        "orchestrator.extraction.status-timeout", "30m"
      )));
      context.register(PollingTestConfiguration.class);

      Exception exception = assertThrows(Exception.class, context::refresh);
      Throwable cause = exception;
      while (cause.getCause() != null) {
        cause = cause.getCause();
      }
      assertTrue(cause.getMessage().contains("status-poll-interval"));
    }
  }

  @Test
  void givenFileTypeTimeoutWhenRefreshThenBindsOverrideAndUsesGlobalFallback() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "30s",
        "orchestrator.extraction.status-timeout", "4h",
        "orchestrator.extraction.status-timeout-by-file-type.ORGANIZATIONS", "1h",
        "orchestrator.extraction.status-timeout-by-file-type.DEBT_POSITIONS_TYPE", "30m"
      )));
      context.register(PollingTestConfiguration.class);
      context.refresh();

      ExtractionPollingProperties properties = context.getBean(ExtractionPollingProperties.class);
      assertEquals(Duration.ofHours(1), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.ORGANIZATIONS));
      assertEquals(Duration.ofMinutes(30), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.DEBT_POSITIONS_TYPE));
      assertEquals(Duration.ofHours(4), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.ASSESSMENTS));
    }
  }

  @ParameterizedTest
  @MethodSource("invalidOverrides")
  void givenInvalidFileTypeTimeoutWhenConstructThenRejectsConfiguration(Duration interval, Duration timeout,
                                                                        String message) {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
      () -> new ExtractionPollingProperties(interval, Duration.ofHours(4),
        Map.of(MigrationFileType.ORGANIZATIONS, timeout)));

    assertEquals("status-timeout-by-file-type[ORGANIZATIONS] " + message, exception.getMessage());
  }

  @Test
  void givenInvalidFileTypeTimeoutWhenRefreshThenFailsAtStartup() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "30s",
        "orchestrator.extraction.status-timeout", "4h",
        "orchestrator.extraction.status-timeout-by-file-type.ORGANIZATIONS", "29m"
      )));
      context.register(PollingTestConfiguration.class);

      Exception exception = assertThrows(Exception.class, context::refresh);
      Throwable cause = exception;
      while (cause.getCause() != null) {
        cause = cause.getCause();
      }
      assertTrue(cause.getMessage().contains("status-timeout-by-file-type[ORGANIZATIONS]"));
    }
  }

  @Test
  void givenUnknownFileTypeWhenRefreshThenFailsAtStartup() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "30s",
        "orchestrator.extraction.status-timeout", "4h",
        "orchestrator.extraction.status-timeout-by-file-type.UNKNOWN_FILE", "1h"
      )));
      context.register(PollingTestConfiguration.class);

      assertThrows(Exception.class, context::refresh);
    }
  }

  private static Stream<Arguments> validDurations() {
    return Stream.of(
      Arguments.of(Duration.ofSeconds(10), Duration.ofMinutes(30)),
      Arguments.of(Duration.ofMinutes(30), Duration.ofMinutes(30)),
      Arguments.of(Duration.ofSeconds(30), Duration.ofHours(4))
    );
  }

  private static Stream<Arguments> invalidDurations() {
    String intervalMessage = "status-poll-interval must be a whole number of seconds and at least 10 seconds";
    String timeoutMessage = "status-timeout must be a whole number of seconds and at least 1800 seconds";
    return Stream.of(
      Arguments.of(null, Duration.ofMinutes(30), intervalMessage),
      Arguments.of(Duration.ZERO, Duration.ofMinutes(30), intervalMessage),
      Arguments.of(Duration.ofSeconds(9), Duration.ofMinutes(30), intervalMessage),
      Arguments.of(Duration.ofMillis(10500), Duration.ofMinutes(30), intervalMessage),
      Arguments.of(Duration.ofSeconds(10), null, timeoutMessage),
      Arguments.of(Duration.ofSeconds(10), Duration.ofSeconds(1799), timeoutMessage),
      Arguments.of(Duration.ofSeconds(10), Duration.ofMillis(1800500), timeoutMessage),
      Arguments.of(Duration.ofSeconds(1801), Duration.ofSeconds(1800),
        "status-timeout must be at least status-poll-interval"),
      Arguments.of(Duration.ofSeconds(10), Duration.ofSeconds(Long.MAX_VALUE / 1_000_000_000L + 1),
        "status-timeout exceeds the maximum monotonic clock interval")
    );
  }

  private static Stream<Arguments> invalidOverrides() {
    return Stream.of(
      Arguments.of(Duration.ofSeconds(10), Duration.ofSeconds(1799),
        "must be a whole number of seconds and at least 1800 seconds"),
      Arguments.of(Duration.ofSeconds(10), Duration.ofMillis(1800500),
        "must be a whole number of seconds and at least 1800 seconds"),
      Arguments.of(Duration.ofSeconds(1801), Duration.ofSeconds(1800),
        "must be at least status-poll-interval"),
      Arguments.of(Duration.ofSeconds(10), Duration.ofSeconds(Long.MAX_VALUE / 1_000_000_000L + 1),
        "exceeds the maximum monotonic clock interval")
    );
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(ExtractionPollingProperties.class)
  static class PollingTestConfiguration {
  }
}
