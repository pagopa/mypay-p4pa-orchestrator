package it.gov.pagopa.mypay2pu.orchestrator.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransferOutcomeConfiguration {

  @Bean
  public Clock transferOutcomeClock() {
    return Clock.systemUTC();
  }
}
