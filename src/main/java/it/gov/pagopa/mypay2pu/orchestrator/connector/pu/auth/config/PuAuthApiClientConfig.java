package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.config;

import it.gov.pagopa.mypay2pu.orchestrator.config.rest.ApiClientConfig;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rest.pu.auth")
@SuperBuilder
@NoArgsConstructor
public class PuAuthApiClientConfig extends ApiClientConfig {
}
