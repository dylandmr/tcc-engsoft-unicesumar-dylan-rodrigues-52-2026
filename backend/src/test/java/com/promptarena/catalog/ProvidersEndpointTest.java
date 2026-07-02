package com.promptarena.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end MockMvc tests for {@code GET /api/providers} (FR-020). All provider keys are forced
 * blank (test properties outrank real environment variables), so every provider is unconfigured and
 * the catalog is fully curated — deterministic and offline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "GOOGLE_API_KEY=",
      "OPENAI_API_KEY=",
      "ANTHROPIC_API_KEY=",
      "XAI_API_KEY=",
      "DEEPSEEK_API_KEY=",
      "GOOGLE_MODEL=",
      "OPENAI_MODEL=",
      "ANTHROPIC_MODEL=",
      "XAI_MODEL=",
      "DEEPSEEK_MODEL="
    })
class ProvidersEndpointTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    mockMvc.perform(get("/api/providers")).andExpect(status().isUnauthorized());
  }

  @Test
  void listsAllProvidersInCanonicalOrderWithCuratedCatalogs() throws Exception {
    mockMvc
        .perform(get("/api/providers").with(user("demo").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.providers.length()").value(5))
        .andExpect(jsonPath("$.providers[0].provider").value("GEMINI"))
        .andExpect(jsonPath("$.providers[1].provider").value("CHATGPT"))
        .andExpect(jsonPath("$.providers[2].provider").value("CLAUDE"))
        .andExpect(jsonPath("$.providers[3].provider").value("GROK"))
        .andExpect(jsonPath("$.providers[4].provider").value("DEEPSEEK"))
        .andExpect(jsonPath("$.providers[0].configured").value(false))
        .andExpect(jsonPath("$.providers[0].defaultModel").value("gemini-2.5-flash"))
        .andExpect(jsonPath("$.providers[0].source").value("curated"))
        // Sorted ascending, deduplicated, and always containing the default model.
        .andExpect(jsonPath("$.providers[0].models[0]").value("gemini-2.0-flash"))
        .andExpect(jsonPath("$.providers[0].models[1]").value("gemini-2.5-flash"))
        .andExpect(jsonPath("$.providers[0].models[2]").value("gemini-2.5-flash-lite"))
        .andExpect(jsonPath("$.providers[0].models[3]").value("gemini-2.5-pro"))
        .andExpect(jsonPath("$.providers[2].defaultModel").value("claude-3-5-sonnet-latest"))
        .andExpect(jsonPath("$.providers[4].defaultModel").value("deepseek-chat"));
  }
}
