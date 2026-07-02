package com.promptarena.comparison;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.promptarena.catalog.ModelCatalogService;
import com.promptarena.dto.ProviderCatalogEntry;
import com.promptarena.model.Comparison;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.model.User;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.repository.UserRepository;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end MockMvc tests for the analysis endpoints (FR-021): the HTTP-level contract of {@code
 * GET /api/comparisons/{id}/analysis/stream} — auth, ownership, and every 400 code rejected before
 * any SSE — plus the recorded analysis on the detail response. SSE event emission itself is covered
 * by {@code ComparisonControllerTest} (MockMvc's async machinery is awkward for SSE). Provider keys
 * are forced blank and the model catalog is mocked, so everything is deterministic and offline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(
    properties = {
      "GOOGLE_API_KEY=",
      "OPENAI_API_KEY=",
      "ANTHROPIC_API_KEY=",
      "XAI_API_KEY=",
      "DEEPSEEK_API_KEY="
    })
class AnalysisEndpointTest {

  /** The stand-in for each provider's live model list (what its own API would report). */
  private static final Map<Provider, List<String>> LIVE_MODELS =
      Map.of(
          Provider.GEMINI, List.of("gemini-2.5-flash", "gemini-2.5-pro"),
          Provider.CHATGPT, List.of("gpt-4o-mini"),
          Provider.CLAUDE, List.of("claude-haiku-4-5", "claude-sonnet-4-5"),
          Provider.GROK, List.of(),
          Provider.DEEPSEEK, List.of("deepseek-chat"));

  @Autowired private MockMvc mockMvc;
  @Autowired private ComparisonRepository comparisons;
  @Autowired private UserRepository users;
  @MockitoBean private ModelCatalogService modelCatalog;

  @BeforeEach
  void stubCatalog() {
    when(modelCatalog.catalogFor(any()))
        .thenAnswer(
            invocation -> {
              Collection<Provider> requested = invocation.getArgument(0);
              Map<Provider, ProviderCatalogEntry> catalog = new EnumMap<>(Provider.class);
              for (Provider provider : requested) {
                List<String> models = LIVE_MODELS.get(provider);
                catalog.put(
                    provider, new ProviderCatalogEntry(provider, !models.isEmpty(), models));
              }
              return catalog;
            });
  }

  private User demo() {
    return users.findByUsernameIgnoreCase("demo").orElseThrow();
  }

  /** A COMPLETE comparison for {@code owner} with one SUCCESS result per given provider. */
  private Comparison completeComparison(User owner, Provider... successes) {
    Comparison comparison = new Comparison(owner, "the prompt", List.of(successes));
    for (Provider provider : successes) {
      comparison.addResult(
          new ProviderResult(provider, Outcome.SUCCESS, "answer-" + provider, null, 10L));
    }
    comparison.markComplete();
    return comparisons.save(comparison);
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    mockMvc
        .perform(get("/api/comparisons/{id}/analysis/stream", "any"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownComparisonReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/comparisons/{id}/analysis/stream", "does-not-exist")
                .param("provider", "CLAUDE")
                .param("model", "claude-haiku-4-5")
                .with(user("demo").roles("USER")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  @Test
  void anotherUsersComparisonReturns404() throws Exception {
    User bob = users.save(new User("bob", "hash"));
    Comparison bobs = completeComparison(bob, Provider.GEMINI, Provider.CLAUDE);

    mockMvc
        .perform(
            get("/api/comparisons/{id}/analysis/stream", bobs.getId())
                .param("provider", "CLAUDE")
                .param("model", "claude-haiku-4-5")
                .with(user("demo").roles("USER")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  @Test
  void pendingComparisonIsRejected() throws Exception {
    Comparison pending =
        comparisons.save(new Comparison(demo(), "p", List.of(Provider.GEMINI, Provider.CLAUDE)));

    mockMvc
        .perform(
            get("/api/comparisons/{id}/analysis/stream", pending.getId())
                .param("provider", "CLAUDE")
                .param("model", "claude-haiku-4-5")
                .with(user("demo").roles("USER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("comparison_not_complete"));
  }

  @Test
  void fewerThanTwoSuccessfulAnswersIsRejected() throws Exception {
    Comparison comparison = completeComparison(demo(), Provider.GEMINI);

    mockMvc
        .perform(
            get("/api/comparisons/{id}/analysis/stream", comparison.getId())
                .param("provider", "CLAUDE")
                .param("model", "claude-haiku-4-5")
                .with(user("demo").roles("USER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("insufficient_results"));
  }

  @Test
  void unknownJudgeProviderIsRejected() throws Exception {
    Comparison comparison = completeComparison(demo(), Provider.GEMINI, Provider.CLAUDE);

    mockMvc
        .perform(
            get("/api/comparisons/{id}/analysis/stream", comparison.getId())
                .param("provider", "FOO")
                .param("model", "bar")
                .with(user("demo").roles("USER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("unknown_provider"));
  }

  @Test
  void absentJudgeProviderIsRejected() throws Exception {
    Comparison comparison = completeComparison(demo(), Provider.GEMINI, Provider.CLAUDE);

    mockMvc
        .perform(
            get("/api/comparisons/{id}/analysis/stream", comparison.getId())
                .with(user("demo").roles("USER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("unknown_provider"));
  }

  @Test
  void absentJudgeModelIsRejected() throws Exception {
    Comparison comparison = completeComparison(demo(), Provider.GEMINI, Provider.CLAUDE);

    mockMvc
        .perform(
            get("/api/comparisons/{id}/analysis/stream", comparison.getId())
                .param("provider", "CLAUDE")
                .with(user("demo").roles("USER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("missing_model"));
  }

  @Test
  void judgeModelOutsideTheLiveSetIsRejected() throws Exception {
    Comparison comparison = completeComparison(demo(), Provider.GEMINI, Provider.CLAUDE);

    mockMvc
        .perform(
            get("/api/comparisons/{id}/analysis/stream", comparison.getId())
                .param("provider", "CLAUDE")
                .param("model", "clod-9000")
                .with(user("demo").roles("USER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("unknown_model"));
  }

  /** An unconfigured judge provider has an empty live set — naturally rejected as unknown_model. */
  @Test
  void unconfiguredJudgeProviderIsRejectedAsUnknownModel() throws Exception {
    Comparison comparison = completeComparison(demo(), Provider.GEMINI, Provider.CLAUDE);

    mockMvc
        .perform(
            get("/api/comparisons/{id}/analysis/stream", comparison.getId())
                .param("provider", "GROK")
                .param("model", "grok-4")
                .with(user("demo").roles("USER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("unknown_model"));
  }

  @Test
  void detailCarriesTheRecordedAnalysis() throws Exception {
    Comparison comparison = completeComparison(demo(), Provider.GEMINI, Provider.CLAUDE);
    comparison.recordAnalysis(
        "as diferenças…",
        Provider.CHATGPT,
        "gpt-4o-mini",
        List.of(Provider.CLAUDE, Provider.GEMINI));
    comparisons.save(comparison);

    mockMvc
        .perform(get("/api/comparisons/{id}", comparison.getId()).with(user("demo").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysis.text").value("as diferenças…"))
        .andExpect(jsonPath("$.analysis.provider").value("CHATGPT"))
        .andExpect(jsonPath("$.analysis.model").value("gpt-4o-mini"))
        .andExpect(jsonPath("$.analysis.labels.A").value("CLAUDE"))
        .andExpect(jsonPath("$.analysis.labels.B").value("GEMINI"));
  }

  @Test
  void detailWithoutAnAnalysisReportsNull() throws Exception {
    Comparison comparison = completeComparison(demo(), Provider.GEMINI, Provider.CLAUDE);

    mockMvc
        .perform(get("/api/comparisons/{id}", comparison.getId()).with(user("demo").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysis").value(nullValue()));
  }
}
