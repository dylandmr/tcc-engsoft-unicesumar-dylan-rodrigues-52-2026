package com.promptarena.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The system clock as a bean, so time-based logic (the model catalog's TTL cache) is deterministic
 * under test. Pure wiring (no business logic) — excluded from the coverage gate.
 */
@Configuration
public class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
