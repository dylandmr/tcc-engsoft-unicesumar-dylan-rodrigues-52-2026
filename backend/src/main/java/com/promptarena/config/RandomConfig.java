package com.promptarena.config;

import java.util.Random;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Injectable randomness for the judge-analysis answer shuffle (FR-021). A bean so tests can inject
 * a seeded {@link Random} and assert the anonymized order deterministically; {@link Random} is
 * thread-safe, so one shared instance serves concurrent requests.
 */
@Configuration
public class RandomConfig {

  @Bean
  public Random random() {
    return new Random();
  }
}
