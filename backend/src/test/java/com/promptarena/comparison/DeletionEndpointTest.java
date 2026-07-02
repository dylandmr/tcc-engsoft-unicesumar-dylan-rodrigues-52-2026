package com.promptarena.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.promptarena.catalog.ModelCatalogService;
import com.promptarena.model.Comparison;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.model.User;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end MockMvc tests for the FR-022 deletion endpoints — {@code DELETE /api/comparisons/{id}}
 * and {@code DELETE /api/comparisons}. Deleting is strictly own-data-only with a non-revealing 404
 * (FR-016), cascades to every recorded child row (results, chosen models, providers, analysis
 * order), requires the CSRF header like every state-changing call, and clearing an already-empty
 * history is an idempotent 204. Provider keys are forced blank and the model catalog is mocked (as
 * in the sibling endpoint tests) so nothing ever touches the network.
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
class DeletionEndpointTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ComparisonRepository comparisons;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  /** Never fetched here — mocked so no live catalog call is even possible (hermeticity). */
  @MockitoBean private ModelCatalogService modelCatalog;

  private User demo() {
    return users.findByUsernameIgnoreCase("demo").orElseThrow();
  }

  /**
   * A COMPLETE comparison carrying everything FR-022 must remove: two provider results, the two
   * element collections written at creation (providers, chosen models), and a recorded analysis
   * with its label order. Flushed so the child rows exist in SQLite before the delete under test.
   */
  private Comparison fullComparison(User owner) {
    Comparison comparison =
        new Comparison(
            owner,
            "the prompt",
            List.of(Provider.CLAUDE, Provider.GEMINI),
            Map.of(Provider.CLAUDE, "claude-haiku-4-5", Provider.GEMINI, "gemini-2.5-pro"));
    comparison.addResult(
        new ProviderResult(Provider.CLAUDE, Outcome.SUCCESS, "answer-claude", null, 10L));
    comparison.addResult(
        new ProviderResult(Provider.GEMINI, Outcome.SUCCESS, "answer-gemini", null, 12L));
    comparison.markComplete();
    comparison.recordAnalysis(
        "## Diferenças",
        Provider.CHATGPT,
        "gpt-4o-mini",
        List.of(Provider.GEMINI, Provider.CLAUDE));
    return comparisons.saveAndFlush(comparison);
  }

  private long rows(String table, String comparisonId) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from " + table + " where comparison_id = ?", Long.class, comparisonId);
    return count;
  }

  @Test
  void deleteOwnComparisonReturns204AndItIsGoneFromListAndDetail() throws Exception {
    Comparison mine = fullComparison(demo());

    mockMvc
        .perform(
            delete("/api/comparisons/{id}", mine.getId())
                .with(user("demo").roles("USER"))
                .with(csrf()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/comparisons/{id}", mine.getId()).with(user("demo").roles("USER")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
    mockMvc
        .perform(get("/api/comparisons").with(user("demo").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comparisons", hasSize(0)));
  }

  @Test
  void deleteCascadesToResultsModelsProvidersAndAnalysisOrder() throws Exception {
    String id = fullComparison(demo()).getId();
    assertThat(rows("provider_results", id)).isEqualTo(2);
    assertThat(rows("comparison_providers", id)).isEqualTo(2);
    assertThat(rows("comparison_models", id)).isEqualTo(2);
    assertThat(rows("comparison_analysis_order", id)).isEqualTo(2);

    mockMvc
        .perform(delete("/api/comparisons/{id}", id).with(user("demo").roles("USER")).with(csrf()))
        .andExpect(status().isNoContent());
    comparisons.flush();

    assertThat(comparisons.findById(id)).isEmpty();
    assertThat(rows("provider_results", id)).isZero();
    assertThat(rows("comparison_providers", id)).isZero();
    assertThat(rows("comparison_models", id)).isZero();
    assertThat(rows("comparison_analysis_order", id)).isZero();
  }

  @Test
  void deleteUnknownComparisonReturns404() throws Exception {
    mockMvc
        .perform(
            delete("/api/comparisons/{id}", "does-not-exist")
                .with(user("demo").roles("USER"))
                .with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  @Test
  void deleteAnotherUsersComparisonReturns404AndTheirRowSurvives() throws Exception {
    User bob = users.save(new User("bob", "hash"));
    Comparison bobs = fullComparison(bob);

    mockMvc
        .perform(
            delete("/api/comparisons/{id}", bobs.getId())
                .with(user("demo").roles("USER"))
                .with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));

    assertThat(comparisons.findById(bobs.getId())).isPresent();
  }

  @Test
  void unauthenticatedDeleteIsRejected() throws Exception {
    mockMvc
        .perform(delete("/api/comparisons/{id}", "any").with(csrf()))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/comparisons").with(csrf())).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteWithoutCsrfTokenIsForbiddenAndTheRowSurvives() throws Exception {
    Comparison mine = fullComparison(demo());

    mockMvc
        .perform(delete("/api/comparisons/{id}", mine.getId()).with(user("demo").roles("USER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/comparisons").with(user("demo").roles("USER")))
        .andExpect(status().isForbidden());

    assertThat(comparisons.findById(mine.getId())).isPresent();
  }

  @Test
  void clearAllDeletesOnlyTheCallersHistory() throws Exception {
    User demo = demo();
    fullComparison(demo);
    fullComparison(demo);
    User bob = users.save(new User("bob", "hash"));
    String bobsId = fullComparison(bob).getId();

    mockMvc
        .perform(delete("/api/comparisons").with(user("demo").roles("USER")).with(csrf()))
        .andExpect(status().isNoContent());
    comparisons.flush();

    mockMvc
        .perform(get("/api/comparisons").with(user("demo").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comparisons", hasSize(0)));
    // Bob's history is untouched — including every child row (FR-016).
    assertThat(comparisons.findByUserOrderByCreatedAtDesc(bob)).hasSize(1);
    assertThat(rows("provider_results", bobsId)).isEqualTo(2);
    assertThat(rows("comparison_analysis_order", bobsId)).isEqualTo(2);
  }

  @Test
  void clearAllOnAnEmptyHistoryIsAnIdempotent204() throws Exception {
    mockMvc
        .perform(delete("/api/comparisons").with(user("demo").roles("USER")).with(csrf()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/comparisons").with(user("demo").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comparisons", hasSize(0)));
  }
}
