package com.promptarena.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security wiring for the harness milestone. The health and actuator-health endpoints are
 * public so the CI smoke test can reach them; everything else requires authentication.
 *
 * <p>Session-based login/logout, CSRF, and BCrypt are added with the authentication story (US3 /
 * tasks T012, T046, T047). CSRF is temporarily disabled until that wiring lands.
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/health", "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
