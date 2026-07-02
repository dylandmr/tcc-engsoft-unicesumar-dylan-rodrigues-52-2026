package com.promptarena.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Deep links to the SPA's client-side routes must serve the app shell even when signed out — the
 * full security chain runs here, so these tests pin both the permit rules and the forward.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpaForwardingControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void loginDeepLinkForwardsToTheSpaShell() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void historyDeepLinkForwardsToTheSpaShellWhileSignedOut() throws Exception {
    mockMvc
        .perform(get("/history"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void resultsDeepLinkForwardsToTheSpaShell() throws Exception {
    mockMvc
        .perform(get("/results/some-comparison-id"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void unknownApiPathsAreStillGuardedNotForwarded() throws Exception {
    mockMvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
  }
}
