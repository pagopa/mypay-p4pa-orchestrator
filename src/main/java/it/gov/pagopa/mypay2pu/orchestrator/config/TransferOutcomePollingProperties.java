package it.gov.pagopa.mypay2pu.orchestrator.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator.transfer-outcome", ignoreUnknownFields = false)
public record TransferOutcomePollingProperties(Duration statusPollInterval, Duration statusTimeout) {

  public TransferOutcomePollingProperties {
    validate("status-poll-interval", statusPollInterval);
    validate("status-timeout", statusTimeout);
    if (statusTimeout.compareTo(statusPollInterval) < 0) {
      throw new IllegalArgumentException("status-timeout must be at least status-poll-interval");
    }
  }

  private static void validate(String name, Duration value) {
    if (value == null || value.isNegative() || value.isZero() || value.getNano() != 0) {
      throw new IllegalArgumentException(name + " must be a positive whole number of seconds");
    }
  }
}
