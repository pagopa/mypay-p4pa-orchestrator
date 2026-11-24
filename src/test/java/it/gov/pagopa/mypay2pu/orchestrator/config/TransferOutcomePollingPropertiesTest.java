package it.gov.pagopa.mypay2pu.orchestrator.config;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class TransferOutcomePollingPropertiesTest {

  @Test
  void givenValidDurationsWhenConstructThenKeepsConfiguredValues() {
    TransferOutcomePollingProperties properties = new TransferOutcomePollingProperties(Duration.ofSeconds(30), Duration.ofHours(12));

    assertEquals(Duration.ofSeconds(30), properties.statusPollInterval());
    assertEquals(Duration.ofHours(12), properties.statusTimeout());
  }

  @ParameterizedTest
  @MethodSource("invalidDurations")
  void givenInvalidDurationsWhenConstructThenRejectsConfiguration(Duration interval, Duration timeout) {
    assertThrows(IllegalArgumentException.class, () -> new TransferOutcomePollingProperties(interval, timeout));
  }

  static Stream<Arguments> invalidDurations() {
    return Stream.of(Arguments.of(null, Duration.ofHours(12)),
      Arguments.of(Duration.ZERO, Duration.ofHours(12)),
      Arguments.of(Duration.ofSeconds(-1), Duration.ofHours(12)),
      Arguments.of(Duration.ofMillis(1500), Duration.ofHours(12)),
      Arguments.of(Duration.ofSeconds(30), null),
      Arguments.of(Duration.ofSeconds(30), Duration.ofSeconds(10)),
      Arguments.of(Duration.ofSeconds(30), Duration.ofMillis(30001)));
  }
}
