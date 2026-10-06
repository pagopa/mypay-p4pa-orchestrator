package it.gov.pagopa.mypay2pu.orchestrator.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class DataSourceConfig {

  @Bean(name = "orchestratorDataSource")
  @ConfigurationProperties("datasource.orchestrator")
  public HikariDataSource orchestratorDataSource() {
    return new HikariDataSource();
  }

  @Bean(name = "orchestratorNamedParameterJdbcTemplate")
  public NamedParameterJdbcTemplate orchestratorNamedParameterJdbcTemplate(
    @Qualifier("orchestratorDataSource") DataSource dataSource
  ) {
    return new NamedParameterJdbcTemplate(dataSource);
  }
}
