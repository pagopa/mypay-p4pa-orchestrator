package it.gov.pagopa.mypay2pu.orchestrator.config;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator.extraction")
public record ExtractionPollingProperties(Duration statusPollInterval, Duration statusTimeout,
                                          Map<MigrationFileType, Duration> statusTimeoutByFileType) {
  public ExtractionPollingProperties {
    validate("status-poll-interval", statusPollInterval, Duration.ofSeconds(10));
    validateTimeout("status-timeout", statusTimeout, statusPollInterval);
    statusTimeoutByFileType = statusTimeoutByFileType == null ? Map.of() : Map.copyOf(statusTimeoutByFileType);
    statusTimeoutByFileType.forEach((type, timeout) ->
      validateTimeout("status-timeout-by-file-type[" + type + "]", timeout, statusPollInterval));
  }

  public Duration resolveFileTypeConfigurationStatusTimeout(MigrationFileType fileType) {
    return statusTimeoutByFileType.getOrDefault(fileType, statusTimeout);
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
