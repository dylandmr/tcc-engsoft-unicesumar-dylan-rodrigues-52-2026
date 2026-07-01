package com.promptarena.comparison;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end MockMvc tests for the comparison endpoints (session auth as the seeded demo user). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ComparisonEndpointTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ComparisonRepository comparisons;
  @Autowired private UserRepository users;

  private MockHttpServletRequestBuilder authedPost(String json) {
    return post("/api/comparisons")
        .with(user("demo").roles("USER"))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private static String requestBody(String prompt, String providersJsonArray) {
    return "{\"prompt\":\"" + prompt + "\",\"providers\":" + providersJsonArray + "}";
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    mockMvc.perform(get("/api/comparisons")).andExpect(status().isUnauthorized());
  }

  @Test
  void createPersistsPendingAndIsListedAndDetailed() throws Exception {
    String response =
        mockMvc
            .perform(authedPost(requestBody("Explain entanglement", "[\"CLAUDE\",\"CHATGPT\"]")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.comparisonId").exists())
            .andExpect(jsonPath("$.providers[0]").value("CLAUDE"))
            .andExpect(jsonPath("$.providers[1]").value("CHATGPT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(response, "$.comparisonId");

    mockMvc
        .perform(get("/api/comparisons/{id}", id).with(user("demo").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.prompt").value("Explain entanglement"))
        .andExpect(jsonPath("$.results").isArray());

    mockMvc
        .perform(get("/api/comparisons").with(user("demo").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comparisons[0].id").value(id))
        .andExpect(jsonPath("$.comparisons[0].providers[0]").value("CLAUDE"));
  }

  @Test
  void emptyPromptIsRejected() throws Exception {
    mockMvc
        .perform(authedPost(requestBody("  ", "[\"CLAUDE\"]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("empty_prompt"));
  }

  @Test
  void nullPromptIsRejected() throws Exception {
    mockMvc
        .perform(authedPost("{\"prompt\":null,\"providers\":[\"CLAUDE\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("empty_prompt"));
  }

  @Test
  void nullProvidersIsRejected() throws Exception {
    mockMvc
        .perform(authedPost("{\"prompt\":\"hi\",\"providers\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("no_providers"));
  }

  @Test
  void promptTooLongIsRejected() throws Exception {
    String longPrompt = "x".repeat(8001);
    mockMvc
        .perform(authedPost(requestBody(longPrompt, "[\"CLAUDE\"]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("prompt_too_long"));
  }

  @Test
  void noProvidersIsRejected() throws Exception {
    mockMvc
        .perform(authedPost(requestBody("hi", "[]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("no_providers"));
  }

  @Test
  void tooManyProvidersIsRejected() throws Exception {
    mockMvc
        .perform(
            authedPost(
                requestBody("hi", "[\"GEMINI\",\"CHATGPT\",\"CLAUDE\",\"GROK\",\"DEEPSEEK\"]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("too_many_providers"));
  }

  @Test
  void duplicateProviderIsRejected() throws Exception {
    mockMvc
        .perform(authedPost(requestBody("hi", "[\"CLAUDE\",\"CLAUDE\"]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("duplicate_provider"));
  }

  @Test
  void unknownProviderIsRejected() throws Exception {
    mockMvc
        .perform(authedPost(requestBody("hi", "[\"FOO\"]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("unknown_provider"));
  }

  @Test
  void detailReflectsRecordedResultsIncludingTelemetry() throws Exception {
    User demo = users.findByUsernameIgnoreCase("demo").orElseThrow();
    Comparison comparison = new Comparison(demo, "saved prompt", List.of(Provider.CLAUDE));
    comparison.addResult(
        new ProviderResult(
            Provider.CLAUDE,
            Outcome.SUCCESS,
            "the answer",
            null,
            1840L,
            320L,
            12L,
            256L,
            "claude-test-20250101"));
    comparison.addResult(
        new ProviderResult(Provider.GEMINI, Outcome.ERROR, null, "rate_limited", null));
    comparison.markComplete();
    String id = comparisons.save(comparison).getId();

    mockMvc
        .perform(get("/api/comparisons/{id}", id).with(user("demo").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results[0].provider").value("CLAUDE"))
        .andExpect(jsonPath("$.results[0].outcome").value("SUCCESS"))
        .andExpect(jsonPath("$.results[0].responseText").value("the answer"))
        .andExpect(jsonPath("$.results[0].responseTimeMs").value(1840))
        .andExpect(jsonPath("$.results[0].firstTokenMs").value(320))
        .andExpect(jsonPath("$.results[0].inputTokens").value(12))
        .andExpect(jsonPath("$.results[0].outputTokens").value(256))
        .andExpect(jsonPath("$.results[0].model").value("claude-test-20250101"))
        .andExpect(jsonPath("$.results[1].outcome").value("ERROR"))
        .andExpect(jsonPath("$.results[1].errorMessage").value("rate_limited"))
        .andExpect(jsonPath("$.results[1].firstTokenMs").value(nullValue()))
        .andExpect(jsonPath("$.results[1].model").value(nullValue()));
  }

  @Test
  void detailForUnknownComparisonReturns404() throws Exception {
    mockMvc
        .perform(get("/api/comparisons/{id}", "does-not-exist").with(user("demo").roles("USER")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }
}
