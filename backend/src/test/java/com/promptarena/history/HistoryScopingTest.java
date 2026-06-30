package com.promptarena.history;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.promptarena.model.Comparison;
import com.promptarena.model.Provider;
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
 * US4 (T050): history is strictly scoped per user (FR-016) — there must be zero cross-user leakage. A
 * user lists and opens only their own comparisons; another user's comparison is simply "not found",
 * never disclosed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HistoryScopingTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private ComparisonRepository comparisons;

  @Test
  void aUserListsOnlyTheirOwnComparisons() throws Exception {
    User alice = users.save(new User("alice", "hash"));
    User bob = users.save(new User("bob", "hash"));
    comparisons.save(new Comparison(alice, "alice-one", List.of(Provider.CLAUDE)));
    comparisons.save(new Comparison(alice, "alice-two", List.of(Provider.CHATGPT)));
    comparisons.save(new Comparison(bob, "bob-secret", List.of(Provider.GEMINI)));

    mockMvc
        .perform(get("/api/comparisons").with(user("alice").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comparisons", hasSize(2)))
        .andExpect(
            jsonPath("$.comparisons[*].prompt", containsInAnyOrder("alice-one", "alice-two")));
  }

  @Test
  void aUserCannotOpenAnotherUsersComparison() throws Exception {
    users.save(new User("alice", "hash"));
    User bob = users.save(new User("bob", "hash"));
    Comparison bobs =
        comparisons.save(new Comparison(bob, "bob-secret", List.of(Provider.GEMINI)));

    mockMvc
        .perform(get("/api/comparisons/{id}", bobs.getId()).with(user("alice").roles("USER")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  @Test
  void aUserWithNoComparisonsSeesAnEmptyList() throws Exception {
    User alice = users.save(new User("alice", "hash"));
    comparisons.save(new Comparison(alice, "alice-one", List.of(Provider.CLAUDE)));
    users.save(new User("bob", "hash"));

    mockMvc
        .perform(get("/api/comparisons").with(user("bob").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comparisons", hasSize(0)));
  }
}
