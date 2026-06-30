package com.promptarena.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security wiring for US1/US2. Health/actuator-health and the bundled SPA's static assets are
 * public; every API route requires authentication and is scoped to the current user (FR-001,
 * FR-016).
 *
 * <p><strong>Auth-for-now:</strong> the comparison endpoints are reachable via <em>HTTP Basic</em>
 * against the seeded demo user (see {@link DataSeeder}), authenticated through
 * {@code PromptArenaUserDetailsService} + {@link #passwordEncoder()}. This is deliberately minimal
 * so US1 can be built and tested behind an authenticated session. <strong>US3 replaces HTTP Basic
 * with session-cookie login/logout and re-enables CSRF on state-changing POSTs</strong> (the SSE
 * GET stays CSRF-exempt because {@code EventSource} cannot set headers).
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/health", "/actuator/health")
                    .permitAll()
                    // Serve the bundled SPA (static assets) same-origin
                    .requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/index.html",
                        "/favicon.ico",
                        "/assets/**",
                        "/*.svg",
                        "/*.png",
                        "/icons.svg")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .httpBasic(Customizer.withDefaults())
        // CSRF stays disabled while auth is HTTP Basic; US3 re-enables it for the session POSTs.
        .csrf(csrf -> csrf.disable());
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
