package com.promptarena.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Concurrency for the provider fan-out. Provider calls are blocking I/O, so a virtual-thread
 * executor lets a slow provider park a cheap thread instead of starving a bounded pool (research
 * Decision 2).
 */
@Configuration
public class ConcurrencyConfig {

  @Bean(destroyMethod = "close")
  public ExecutorService providerExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
