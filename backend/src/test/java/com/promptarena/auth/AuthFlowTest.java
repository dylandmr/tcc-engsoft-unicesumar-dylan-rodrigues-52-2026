package com.promptarena.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * US3 (T044): the session login journey against the seeded demo user. Invalid credentials produce a
 * non-revealing {@code 401 invalid_credentials}; a valid login establishes a session that
 * authorizes {@code /api/auth/me}; logout tears the session down.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

  @Autowired private MockMvc mockMvc;

  private static String creds(String username, String password) {
    return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
  }

  @Test
  void invalidCredentialsReturnNonRevealing401() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(creds("demo", "wrong-password")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_credentials"));
  }

  @Test
  void unknownUserReturnsTheSameNonRevealing401() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(creds("nobody", "whatever")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_credentials"));
  }

  @Test
  void validLoginEstablishesAnAuthenticatedSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(creds("demo", "demo1234")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("demo"))
            .andReturn();

    MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

    mockMvc
        .perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("demo"));
  }

  @Test
  void meWithoutASessionIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutEndsTheSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(creds("demo", "demo1234")))
            .andExpect(status().isOk())
            .andReturn();
    MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

    mockMvc
        .perform(post("/api/auth/logout").with(csrf()).session(session))
        .andExpect(status().isNoContent());
  }
}
