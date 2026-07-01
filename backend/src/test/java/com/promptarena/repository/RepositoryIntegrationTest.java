package com.promptarena.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.promptarena.model.Comparison;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.model.Status;
import com.promptarena.model.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RepositoryIntegrationTest {

  @Autowired private UserRepository users;
  @Autowired private ComparisonRepository comparisons;

  @Test
  void findsUserByUsernameCaseInsensitively() {
    users.save(new User("Alice", "hash"));

    assertThat(users.findByUsernameIgnoreCase("alice")).isPresent();
    assertThat(users.findByUsernameIgnoreCase("ALICE")).isPresent();
    assertThat(users.findByUsernameIgnoreCase("bob")).isEmpty();
  }

  @Test
  void persistsComparisonWithResultsAndScopesByUser() {
    User alice = users.save(new User("alice", "hash"));
    User bob = users.save(new User("bob", "hash"));

    Comparison comparison = new Comparison(alice, "explain entanglement");
    comparison.addResult(
        new ProviderResult(
            Provider.CLAUDE, Outcome.SUCCESS, "answer", null, 1840L, 320L, 12L, 256L, "claude-x"));
    comparison.addResult(
        new ProviderResult(Provider.GEMINI, Outcome.TIMEOUT, null, "Timed out", null));
    comparison.markComplete();
    Comparison saved = comparisons.save(comparison);

    assertThat(saved.getStatus()).isEqualTo(Status.COMPLETE);
    assertThat(saved.getResults()).hasSize(2);

    ProviderResult withTelemetry = saved.getResults().get(0);
    assertThat(withTelemetry.getFirstTokenMs()).isEqualTo(320L);
    assertThat(withTelemetry.getInputTokens()).isEqualTo(12L);
    assertThat(withTelemetry.getOutputTokens()).isEqualTo(256L);
    assertThat(withTelemetry.getModel()).isEqualTo("claude-x");
    ProviderResult withoutTelemetry = saved.getResults().get(1);
    assertThat(withoutTelemetry.getFirstTokenMs()).isNull();
    assertThat(withoutTelemetry.getModel()).isNull();

    List<Comparison> aliceHistory = comparisons.findByUserOrderByCreatedAtDesc(alice);
    assertThat(aliceHistory).extracting(Comparison::getId).containsExactly(saved.getId());
    assertThat(comparisons.findByUserOrderByCreatedAtDesc(bob)).isEmpty();
  }

  @Test
  void findByIdAndUserEnforcesOwnership() {
    User alice = users.save(new User("alice", "hash"));
    User bob = users.save(new User("bob", "hash"));
    Comparison saved = comparisons.save(new Comparison(alice, "a prompt"));

    assertThat(comparisons.findByIdAndUser(saved.getId(), alice)).isPresent();

    Optional<Comparison> asBob = comparisons.findByIdAndUser(saved.getId(), bob);
    assertThat(asBob).isEmpty();
  }
}
