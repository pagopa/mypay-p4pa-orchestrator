package it.gov.pagopa.mypay2pu.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    return http
      .csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/actuator/health/**", "/actuator/info", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
        .anyRequest().authenticated())
      .httpBasic(Customizer.withDefaults())
      .formLogin(AbstractHttpConfigurer::disable)
      .logout(AbstractHttpConfigurer::disable)
      .build();
  }

  @Bean
  public UserDetailsService userDetailsService(BasicAuthProperties orchestratorBasicAuthProperties) {
    return new InMemoryUserDetailsManager(
      User.withUsername(orchestratorBasicAuthProperties.username())
        .password("{noop}" + orchestratorBasicAuthProperties.password())
        .roles("ORCHESTRATOR")
        .build()
    );
  }
}
