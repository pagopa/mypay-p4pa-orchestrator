package it.gov.pagopa.mypay2pu.orchestrator.config;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator.extraction", ignoreUnknownFields = false)
public record ExtractionPollingProperties(Duration statusPollInterval, Duration statusTimeout,
                                          Timeouts timeouts) {
  public ExtractionPollingProperties {
    validate("status-poll-interval", statusPollInterval, Duration.ofSeconds(10));
    validateTimeout("status-timeout", statusTimeout, statusPollInterval);
    if (timeouts == null || timeouts.defaults() == null) {
      throw new IllegalArgumentException("timeouts.defaults must define short-running and long-running");
    }
    validateTimeout("timeouts.defaults.short-running", timeouts.defaults().shortRunning(), statusPollInterval);
    validateTimeout("timeouts.defaults.long-running", timeouts.defaults().longRunning(), statusPollInterval);
    timeouts.statusTimeoutByFileType().forEach((fileType, timeout) -> {
      if (!timeout.equals(timeouts.defaults().shortRunning()) && !timeout.equals(timeouts.defaults().longRunning())) {
        throw new IllegalArgumentException("timeouts.status-timeout-by-file-type[" + fileType
          + "] must equal short-running or long-running");
      }
    });
  }

  public Duration resolveFileTypeConfigurationStatusTimeout(MigrationFileType fileType) {
    return timeouts.statusTimeoutByFileType().getOrDefault(fileType, statusTimeout);
  }

  public record Timeouts(Defaults defaults, Map<MigrationFileType, Duration> statusTimeoutByFileType) {
    public Timeouts {
      statusTimeoutByFileType = statusTimeoutByFileType == null ? Map.of() : Map.copyOf(statusTimeoutByFileType);
    }
  }

  public record Defaults(Duration shortRunning, Duration longRunning) {
  }

  private static void validateTimeout(String name, Duration timeout, Duration interval) {
    validate(name, timeout, Duration.ofMinutes(30));
    if (timeout.compareTo(interval) < 0) {
      throw new IllegalArgumentException(name + " must be at least status-poll-interval");
    }
    if (timeout.getSeconds() > Long.MAX_VALUE / 1_000_000_000L) {
      throw new IllegalArgumentException(name + " exceeds the maximum monotonic clock interval");
    }
  }

  private static void validate(String name, Duration value, Duration minimum) {
    if (value == null || value.compareTo(minimum) < 0 || value.getNano() != 0) {
      throw new IllegalArgumentException(name + " must be a whole number of seconds and at least " + minimum.toSeconds() + " seconds");
    }
  }
}
