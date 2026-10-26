package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config;

import it.gov.pagopa.mypay2pu.orchestrator.config.rest.ApiClientConfig;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rest.extractor.basic-auth")
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class ExtractorApiClientConfig extends ApiClientConfig {
  private String username;
  private String password;
}
