package com.promptarena.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * US3 (T043): every API route except the public ones requires an authenticated session. Unauthenticated
 * requests are rejected with {@code 401} (not redirected to a login page), so the SPA can react in code.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProtectedRouteTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void comparisonsListRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/comparisons")).andExpect(status().isUnauthorized());
  }

  @Test
  void currentUserEndpointRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void comparisonStreamRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/comparisons/{id}/stream", "any-id"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void healthEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
  }

  @Test
  void authenticatedUserReachesProtectedRoute() throws Exception {
    mockMvc
        .perform(get("/api/comparisons").with(user("demo").roles("USER")))
        .andExpect(status().isOk());
  }
}
