package it.gov.pagopa.mypay2pu.orchestrator.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Storage settings shared by Extract and Transfer operations. */
@ConfigurationProperties(prefix = "orchestrator")
public record MigrationTransferProperties(Storage storage) {

  public MigrationTransferProperties {
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(storage.extractorOutputPath(), "storage.extractorOutputPath");
  }

  public record Storage(String extractorOutputPath) {
  }
}
