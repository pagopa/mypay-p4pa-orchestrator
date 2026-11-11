package it.gov.pagopa.mypay2pu.orchestrator.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Storage and broker settings shared by Transfer operations. */
@ConfigurationProperties(prefix = "orchestrator")
public record MigrationTransferProperties(Storage storage, Transfer transfer) {

  public MigrationTransferProperties {
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(storage.extractorOutputPath(), "storage.extractorOutputPath");
    Objects.requireNonNull(transfer, "transfer");
  }

  public record Storage(String extractorOutputPath) {
  }

  public record Transfer(String brokerIpaCode) {
  }
}
