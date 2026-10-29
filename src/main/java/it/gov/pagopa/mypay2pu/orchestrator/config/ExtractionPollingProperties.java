package it.gov.pagopa.mypay2pu.orchestrator.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator.extraction")
public record ExtractionPollingProperties(Duration statusPollInterval, Duration statusTimeout) {
}
