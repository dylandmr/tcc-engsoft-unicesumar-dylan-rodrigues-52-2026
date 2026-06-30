package com.promptarena.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.promptarena.comparison.ComparisonService;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Comparison;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.model.Status;
import com.promptarena.model.User;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderRegistry;
import com.promptarena.provider.ProviderResultMapper;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.repository.UserRepository;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * US4 (T051): once the fan-out completes, the comparison is persisted as {@code COMPLETE} including
 * the outcomes of providers that failed — so history faithfully shows what happened to every
 * provider, not just the ones that succeeded (FR-013, FR-014). Drives the real persistence path
 * with a stubbed provider registry (one success, one failure).
 */
@SpringBootTest
@Transactional
class PersistenceTest {

  @Autowired private ComparisonService comparisonService;
  @Autowired private ComparisonRepository comparisons;
  @Autowired private UserRepository users;

  @MockitoBean private ProviderRegistry registry;

  private static LlmProvider adapter(Provider id, Function<Provider, ProviderResponse> behavior) {
    return new LlmProvider() {
      @Override
      public Provider id() {
        return id;
      }

      @Override
      public ProviderResponse complete(PromptRequest request) {
        return behavior.apply(id);
      }
    };
  }

  @Test
  void completedComparisonPersistsFailedProviderOutcomes() {
    User alice = users.save(new User("alice", "hash"));
    Comparison pending =
        comparisons.save(
            new Comparison(
                alice, "explain entanglement", List.of(Provider.CLAUDE, Provider.GEMINI)));

    when(registry.get(Provider.CLAUDE))
        .thenReturn(
            adapter(Provider.CLAUDE, id -> ProviderResultMapper.success(id, "the answer", 12L)));
    when(registry.get(Provider.GEMINI))
        .thenReturn(
            adapter(
                Provider.GEMINI,
                id -> {
                  throw new IllegalStateException("rate_limited");
                }));

    int reported = comparisonService.execute(pending.getId(), event -> {});

    assertThat(reported).isEqualTo(2);

    Comparison saved = comparisons.findById(pending.getId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(Status.COMPLETE);
    assertThat(saved.getResults())
        .extracting(ProviderResult::getOutcome)
        .containsExactlyInAnyOrder(Outcome.SUCCESS, Outcome.ERROR);

    ProviderResult failed =
        saved.getResults().stream()
            .filter(result -> result.getOutcome() == Outcome.ERROR)
            .findFirst()
            .orElseThrow();
    assertThat(failed.getProvider()).isEqualTo(Provider.GEMINI);
    assertThat(failed.getErrorMessage()).contains("rate_limited");
    assertThat(failed.getResponseText()).isNull();
  }
}
