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
 * blank (test properties outrank real environment variables), so every provider is unconfigured, no
 * live fetch ever happens, and every catalog is empty — deterministic and offline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "GOOGLE_API_KEY=",
      "OPENAI_API_KEY=",
      "ANTHROPIC_API_KEY=",
      "XAI_API_KEY=",
      "DEEPSEEK_API_KEY="
    })
class ProvidersEndpointTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    mockMvc.perform(get("/api/providers")).andExpect(status().isUnauthorized());
  }

  @Test
  void listsAllProvidersInCanonicalOrderWithEmptyCatalogsWhenUnconfigured() throws Exception {
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
        // Unconfigured providers report an empty live catalog — nothing is selectable.
        .andExpect(jsonPath("$.providers[0].models").isEmpty())
        .andExpect(jsonPath("$.providers[4].models").isEmpty())
        // FR-020 v2: no default (or curated-source) model fields exist anywhere.
        .andExpect(jsonPath("$.providers[0].defaultModel").doesNotExist())
        .andExpect(jsonPath("$.providers[0].source").doesNotExist());
  }
}
