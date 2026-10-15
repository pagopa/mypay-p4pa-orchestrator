package it.gov.pagopa.mypay2pu.orchestrator.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

  @Bean("orchestratorTaskExecutor")
  public Executor orchestratorTaskExecutor(
    @Value("${orchestrator.async.core-pool-size}") int corePoolSize,
    @Value("${orchestrator.async.max-pool-size}") int maxPoolSize,
    @Value("${orchestrator.async.queue-capacity}") int queueCapacity
  ) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("orchestrator-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    return executor;
  }
}
