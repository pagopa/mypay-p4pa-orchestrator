package it.gov.pagopa.mypay2pu.orchestrator.config;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties.Defaults;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties.Timeouts;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionPollingPropertiesTest {

  @ParameterizedTest
  @MethodSource("validDurations")
  void givenValidDurationsWhenConstructThenRetainsValues(Duration interval, Duration timeout) {
    ExtractionPollingProperties properties = new ExtractionPollingProperties(interval, timeout, defaults());

    assertEquals(interval, properties.statusPollInterval());
    assertEquals(timeout, properties.statusTimeout());
  }

  @ParameterizedTest
  @MethodSource("invalidDurations")
  void givenInvalidDurationsWhenConstructThenRejectsConfiguration(Duration interval, Duration timeout, String message) {
    Timeouts defaults = defaults();
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class, () -> new ExtractionPollingProperties(interval, timeout, defaults)
    );

    assertEquals(message, exception.getMessage());
  }

  @Test
  void givenInvalidPollingIntervalWhenRefreshThenFailsAtStartup() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "9s",
        "orchestrator.extraction.status-timeout", "30m",
        "orchestrator.extraction.timeouts.defaults.short-running", "30m",
        "orchestrator.extraction.timeouts.defaults.long-running", "1h"
      )));
      context.register(PollingTestConfiguration.class);

      Throwable cause = assertThrows(Exception.class, context::refresh);
      while (cause.getCause() != null) {
        cause = cause.getCause();
      }
      assertTrue(cause.getMessage().contains("status-poll-interval"));
    }
  }

  @Test
  void givenApplicationYamlWhenRefreshThenBindsProfilesAndGlobalFallback() throws Exception {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
        .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "EXTRACTION_STATUS_POLL_INTERVAL", "30s",
        "EXTRACTION_STATUS_TIMEOUT", "4h",
        "EXTRACTION_TIMEOUT_SHORT_RUNNING", "30m",
        "EXTRACTION_TIMEOUT_LONG_RUNNING", "1h"
      )));
      context.register(PollingTestConfiguration.class);
      context.refresh();

      ExtractionPollingProperties properties = context.getBean(ExtractionPollingProperties.class);
      assertEquals(Duration.ofMinutes(30), properties.timeouts().defaults().shortRunning());
      assertEquals(Duration.ofHours(1), properties.timeouts().defaults().longRunning());
      assertEquals(Duration.ofHours(4), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.ORGANIZATIONS));
    }
  }

  @Test
  void givenFileTypeTimeoutWhenRefreshThenBindsOverrideAndUsesGlobalFallback() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "30s",
        "orchestrator.extraction.status-timeout", "4h",
        "orchestrator.extraction.timeouts.defaults.short-running", "35m",
        "orchestrator.extraction.timeouts.defaults.long-running", "2h",
        "orchestrator.extraction.timeouts.status-timeout-by-file-type.ORGANIZATIONS", "${orchestrator.extraction.timeouts.defaults.long-running}",
        "orchestrator.extraction.timeouts.status-timeout-by-file-type.DEBT_POSITIONS_TYPE", "${orchestrator.extraction.timeouts.defaults.short-running}"
      )));
      context.register(PollingTestConfiguration.class);
      context.refresh();

      ExtractionPollingProperties properties = context.getBean(ExtractionPollingProperties.class);
      assertEquals(Duration.ofHours(2), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.ORGANIZATIONS));
      assertEquals(Duration.ofMinutes(35), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.DEBT_POSITIONS_TYPE));
      assertEquals(Duration.ofHours(4), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.ASSESSMENTS));
      assertEquals(Map.of(MigrationFileType.ORGANIZATIONS, Duration.ofHours(2),
        MigrationFileType.DEBT_POSITIONS_TYPE, Duration.ofMinutes(35)), properties.timeouts().statusTimeoutByFileType());
    }
  }

  @ParameterizedTest
  @MethodSource("invalidProfiles")
  void givenInvalidProfileTimeoutWhenConstructThenRejectsConfiguration(Duration interval, Duration timeout,
                                                                        boolean shortRunning, String message) {
    Duration statusTimeout = Duration.ofHours(4);
    Defaults defaults = new Defaults(shortRunning ? timeout : Duration.ofHours(1),
      shortRunning ? Duration.ofHours(1) : timeout);
    Timeouts timeouts = new Timeouts(defaults, null);
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
      () -> new ExtractionPollingProperties(interval, statusTimeout, timeouts)
    );

    assertEquals("timeouts.defaults." + (shortRunning ? "short-running" : "long-running") + " " + message,
      exception.getMessage());
  }

  @Test
  void givenInvalidProfileTimeoutWhenRefreshThenFailsAtStartup() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "30s",
        "orchestrator.extraction.status-timeout", "4h",
        "orchestrator.extraction.timeouts.defaults.short-running", "29m",
        "orchestrator.extraction.timeouts.defaults.long-running", "1h"
      )));
      context.register(PollingTestConfiguration.class);

      Throwable cause = assertThrows(Exception.class, context::refresh);
      while (cause.getCause() != null) {
        cause = cause.getCause();
      }
      assertTrue(cause.getMessage().contains("timeouts.defaults.short-running"));
    }
  }

  @Test
  void givenUnknownFileTypeWhenRefreshThenFailsAtStartup() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "30s",
        "orchestrator.extraction.status-timeout", "4h",
        "orchestrator.extraction.timeouts.defaults.short-running", "30m",
        "orchestrator.extraction.timeouts.defaults.long-running", "1h",
        "orchestrator.extraction.timeouts.status-timeout-by-file-type.UNKNOWN_FILE", "1h"
      )));
      context.register(PollingTestConfiguration.class);

      assertThrows(Exception.class, context::refresh);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"45m", "medium-running", "short-running"})
  void givenInvalidProfileAssignmentWhenRefreshThenFailsAtStartup(String profile) {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "30s",
        "orchestrator.extraction.status-timeout", "4h",
        "orchestrator.extraction.timeouts.defaults.short-running", "30m",
        "orchestrator.extraction.timeouts.defaults.long-running", "1h",
        "orchestrator.extraction.timeouts.status-timeout-by-file-type.ORGANIZATIONS", profile
      )));
      context.register(PollingTestConfiguration.class);
      assertThrows(Exception.class, context::refresh);
    }
  }

  @Test
  void givenAllowedLiteralDurationsWhenConstructThenUsesConfiguredValues() {
    Defaults defaults = new Defaults(Duration.ofMinutes(30), Duration.ofHours(1));
    Timeouts timeouts = new Timeouts(defaults,
      Map.of(MigrationFileType.ORGANIZATIONS, Duration.ofSeconds(1800),
        MigrationFileType.ASSESSMENTS, Duration.ofMinutes(60)));
    ExtractionPollingProperties properties = new ExtractionPollingProperties(Duration.ofSeconds(30), Duration.ofHours(4),
      timeouts);

    assertEquals(Duration.ofMinutes(30), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.ORGANIZATIONS));
    assertEquals(Duration.ofHours(1), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.ASSESSMENTS));
    assertEquals(Duration.ofHours(4), properties.resolveFileTypeConfigurationStatusTimeout(MigrationFileType.DEBT_POSITIONS_TYPE));
  }

  @Test
  void givenOtherDurationWhenConstructThenRejectsConfiguration() {
    Defaults defaults = new Defaults(Duration.ofMinutes(30), Duration.ofHours(1));
    Timeouts timeouts = new Timeouts(defaults,
      Map.of(MigrationFileType.ORGANIZATIONS, Duration.ofMinutes(45)));
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
      () -> new ExtractionPollingProperties(Duration.ofSeconds(30), Duration.ofHours(4), timeouts)
    );
    assertEquals("timeouts.status-timeout-by-file-type[ORGANIZATIONS] must equal short-running or long-running",
      exception.getMessage());
  }

  @Test
  void givenLegacyFileTypeTimeoutWhenRefreshThenFailsAtStartup() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "orchestrator.extraction.status-poll-interval", "30s",
        "orchestrator.extraction.status-timeout", "4h",
        "orchestrator.extraction.timeouts.defaults.short-running", "30m",
        "orchestrator.extraction.timeouts.defaults.long-running", "1h",
        "orchestrator.extraction.status-timeout-by-file-type.ORGANIZATIONS", "1h"
      )));
      context.register(PollingTestConfiguration.class);
      assertThrows(Exception.class, context::refresh);
    }
  }

  @Test
  void givenMissingProfilesWhenConstructThenRejectsConfiguration() {
    Duration statusPollInterval = Duration.ofSeconds(30);
    Duration statusTimeout = Duration.ofHours(4);
    Timeouts timeouts = new Timeouts(null, null);
    assertThrows(IllegalArgumentException.class,
      () -> new ExtractionPollingProperties(statusPollInterval, statusTimeout, null));
    assertThrows(IllegalArgumentException.class,
      () -> new ExtractionPollingProperties(statusPollInterval, statusTimeout, timeouts));
  }

  private static Timeouts defaults() {
    return new Timeouts(new Defaults(Duration.ofMinutes(30), Duration.ofHours(1)), null);
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

  private static Stream<Arguments> invalidProfiles() {
    return Stream.of(
      Arguments.of(Duration.ofSeconds(10), Duration.ofSeconds(1799), true,
        "must be a whole number of seconds and at least 1800 seconds"),
      Arguments.of(Duration.ofSeconds(10), Duration.ofMillis(1800500), false,
        "must be a whole number of seconds and at least 1800 seconds"),
      Arguments.of(Duration.ofSeconds(1801), Duration.ofSeconds(1800), true,
        "must be at least status-poll-interval"),
      Arguments.of(Duration.ofSeconds(10), Duration.ofSeconds(Long.MAX_VALUE / 1_000_000_000L + 1), false,
        "exceeds the maximum monotonic clock interval")
    );
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(ExtractionPollingProperties.class)
  static class PollingTestConfiguration {
  }
}
