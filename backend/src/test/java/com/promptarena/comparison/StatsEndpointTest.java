package com.promptarena.comparison;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.promptarena.model.Comparison;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.model.User;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end MockMvc tests for {@code GET /api/comparisons/stats} (FR-023): per-provider aggregates
 * derived at read time from the caller's own recorded provider results. Requires auth, is strictly
 * user-scoped (FR-016), answers an empty list for an empty history, and the literal {@code stats}
 * path must win over the {@code /{id}} detail route.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsEndpointTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private ComparisonRepository comparisons;

  /** One flushed comparison for {@code owner} carrying the given recorded results. */
  private void comparisonWith(User owner, ProviderResult... results) {
    Comparison comparison =
        new Comparison(
            owner,
            "the prompt",
            List.of(results).stream().map(ProviderResult::getProvider).toList());
    for (ProviderResult result : results) {
      comparison.addResult(result);
    }
    comparison.markComplete();
    comparisons.saveAndFlush(comparison);
  }

  private static ProviderResult result(
      Provider provider,
      Outcome outcome,
      Long responseTimeMs,
      Long firstTokenMs,
      Long outputTokens) {
    return new ProviderResult(
        provider, outcome, "answer", null, responseTimeMs, firstTokenMs, null, outputTokens, null);
  }

  @Test
  void unauthenticatedStatsRequestIsRejected() throws Exception {
    mockMvc.perform(get("/api/comparisons/stats")).andExpect(status().isUnauthorized());
  }

  @Test
  void aUserWithNoHistoryGetsAnEmptyStatsList() throws Exception {
    users.save(new User("alice", "hash"));

    mockMvc
        .perform(get("/api/comparisons/stats").with(user("alice").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats", hasSize(0)));
  }

  @Test
  void statsPathIsNotSwallowedByTheDetailRoute() throws Exception {
    // The /{id} handler would answer 404 {"error":"not_found"} for the unknown id "stats"; the
    // literal route must answer 200 with the stats shape instead.
    users.save(new User("alice", "hash"));

    mockMvc
        .perform(get("/api/comparisons/stats").with(user("alice").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats").isArray())
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void aggregatesAreDerivedFromTheCallersRecordedResults() throws Exception {
    User alice = users.save(new User("alice", "hash"));
    comparisonWith(
        alice,
        result(Provider.GEMINI, Outcome.SUCCESS, 2000L, 500L, 60L), // 40 t/s
        result(Provider.CLAUDE, Outcome.SUCCESS, 3000L, null, null)); // latency only
    comparisonWith(
        alice,
        result(Provider.GEMINI, Outcome.SUCCESS, 1000L, 500L, 25L), // 50 t/s
        result(Provider.CLAUDE, Outcome.EMPTY, 1000L, 1000L, 0L), // zero window: no rate
        result(Provider.CHATGPT, Outcome.ERROR, null, null, null));
    comparisonWith(alice, result(Provider.GEMINI, Outcome.TIMEOUT, null, null, null));

    mockMvc
        .perform(get("/api/comparisons/stats").with(user("alice").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats", hasSize(3)))
        // Fastest average first: GEMINI (1500) < CLAUDE (2000); CHATGPT (no telemetry) last.
        .andExpect(jsonPath("$.stats[0].provider").value("GEMINI"))
        .andExpect(jsonPath("$.stats[0].runs").value(3))
        .andExpect(jsonPath("$.stats[0].successes").value(2))
        .andExpect(jsonPath("$.stats[0].empties").value(0))
        .andExpect(jsonPath("$.stats[0].errors").value(0))
        .andExpect(jsonPath("$.stats[0].timeouts").value(1))
        .andExpect(jsonPath("$.stats[0].telemetryRuns").value(2))
        .andExpect(jsonPath("$.stats[0].avgResponseTimeMs").value(1500))
        .andExpect(jsonPath("$.stats[0].avgFirstTokenMs").value(500))
        .andExpect(jsonPath("$.stats[0].avgTokensPerSecond").value(45.0))
        .andExpect(jsonPath("$.stats[1].provider").value("CLAUDE"))
        .andExpect(jsonPath("$.stats[1].runs").value(2))
        .andExpect(jsonPath("$.stats[1].successes").value(1))
        .andExpect(jsonPath("$.stats[1].empties").value(1))
        .andExpect(jsonPath("$.stats[1].errors").value(0))
        .andExpect(jsonPath("$.stats[1].timeouts").value(0))
        .andExpect(jsonPath("$.stats[1].telemetryRuns").value(2))
        .andExpect(jsonPath("$.stats[1].avgResponseTimeMs").value(2000))
        .andExpect(jsonPath("$.stats[1].avgFirstTokenMs").value(1000))
        .andExpect(jsonPath("$.stats[1].avgTokensPerSecond").value(nullValue()))
        .andExpect(jsonPath("$.stats[2].provider").value("CHATGPT"))
        .andExpect(jsonPath("$.stats[2].runs").value(1))
        .andExpect(jsonPath("$.stats[2].errors").value(1))
        .andExpect(jsonPath("$.stats[2].telemetryRuns").value(0))
        .andExpect(jsonPath("$.stats[2].avgResponseTimeMs").value(nullValue()))
        .andExpect(jsonPath("$.stats[2].avgFirstTokenMs").value(nullValue()))
        .andExpect(jsonPath("$.stats[2].avgTokensPerSecond").value(nullValue()));
  }

  @Test
  void anotherUsersResultsNeverLeakIntoTheCallersStats() throws Exception {
    User alice = users.save(new User("alice", "hash"));
    User bob = users.save(new User("bob", "hash"));
    comparisonWith(alice, result(Provider.GEMINI, Outcome.SUCCESS, 1000L, 200L, 40L));
    comparisonWith(bob, result(Provider.GEMINI, Outcome.ERROR, null, null, null));
    comparisonWith(bob, result(Provider.GROK, Outcome.SUCCESS, 500L, 100L, 20L));

    // Alice sees only her own single GEMINI run — bob's error and his GROK entry are absent.
    mockMvc
        .perform(get("/api/comparisons/stats").with(user("alice").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats", hasSize(1)))
        .andExpect(jsonPath("$.stats[0].provider").value("GEMINI"))
        .andExpect(jsonPath("$.stats[0].runs").value(1))
        .andExpect(jsonPath("$.stats[0].successes").value(1))
        .andExpect(jsonPath("$.stats[0].errors").value(0));
  }
}
