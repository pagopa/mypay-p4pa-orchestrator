package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config;

import it.gov.pagopa.mypay2pu.orchestrator.config.rest.ApiClientConfig;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rest.pu.migration")
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class PuMigrationApiClientConfig extends ApiClientConfig {
  private String brokerIpaCode;
}
