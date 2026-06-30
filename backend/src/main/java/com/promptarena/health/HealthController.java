package com.promptarena.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight liveness endpoint used by the CI Docker smoke test (task T064). */
@RestController
public class HealthController {

  @GetMapping("/api/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }
}
