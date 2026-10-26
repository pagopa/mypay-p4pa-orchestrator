package it.gov.pagopa.mypay2pu.orchestrator.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Application-level HTTP Basic credentials, shared with the Extractor service. */
@ConfigurationProperties(prefix = "basic-auth")
@Validated
public record BasicAuthProperties(
  @NotBlank String username,
  @NotBlank String password
) {}
