package com.promptarena.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.dto.ResultEvent;
import com.promptarena.dto.StreamTelemetry;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

  @Mock private ProviderRegistry registry;
  @Mock private ComparisonRepository comparisons;

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private ComparisonService service(long timeoutMs) {
    return new ComparisonService(registry, executor, comparisons, timeoutMs);
  }

  /** An adapter whose behavior is supplied per provider; it streams its text as one token. */
  private static LlmProvider adapter(Provider id, Function<Provider, ProviderResponse> behavior) {
    return new LlmProvider() {
      @Override
      public Provider id() {
        return id;
      }

      @Override
      public ProviderResponse stream(PromptRequest request, java.util.function.Consumer<String> t) {
        ProviderResponse response = behavior.apply(id);
        if (response.text() != null) {
          t.accept(response.text());
        }
        return response;
      }
    };
  }

  private static ProviderResponse ok(Provider id) {
    return ProviderResultMapper.success(
        id, "answer-" + id, 10L, new StreamTelemetry(3L, 7L, 11L, "model-" + id));
  }

  private void register(Map<Provider, LlmProvider> adapters) {
    adapters.forEach((id, adapter) -> lenient().when(registry.get(id)).thenReturn(adapter));
  }

  @Test
  void allProvidersSucceed() {
    List<Provider> providers = List.of(Provider.CLAUDE, Provider.CHATGPT, Provider.GEMINI);
    register(
        Map.of(
            Provider.CLAUDE, adapter(Provider.CLAUDE, ComparisonServiceTest::ok),
            Provider.CHATGPT, adapter(Provider.CHATGPT, ComparisonServiceTest::ok),
            Provider.GEMINI, adapter(Provider.GEMINI, ComparisonServiceTest::ok)));
    // Results arrive concurrently from each provider's virtual thread — collect thread-safely.
    List<ProviderResponse> emitted = new CopyOnWriteArrayList<>();

    List<ProviderResponse> responses =
        service(45_000)
            .fanOut(providers, provider -> new PromptRequest("hi"), (p, t) -> {}, emitted::add);

    assertThat(responses).extracting(ProviderResponse::outcome).containsOnly(Outcome.SUCCESS);
    assertThat(emitted).hasSize(3);
  }

  @Test
  void fanOutForwardsStreamedTokensPerProvider() {
    List<Provider> providers = List.of(Provider.CLAUDE);
    register(Map.of(Provider.CLAUDE, adapter(Provider.CLAUDE, ComparisonServiceTest::ok)));
    Map<Provider, String> tokens = new ConcurrentHashMap<>();

    service(45_000)
        .fanOut(
            providers,
            provider -> new PromptRequest("hi"),
            (provider, token) -> tokens.merge(provider, token, String::concat),
            r -> {});

    assertThat(tokens.get(Provider.CLAUDE)).isEqualTo("answer-" + Provider.CLAUDE);
  }

  @Test
  void oneProviderErrorsWhileOthersSucceed() {
    List<Provider> providers = List.of(Provider.CLAUDE, Provider.CHATGPT);
    register(
        Map.of(
            Provider.CLAUDE,
                adapter(
                    Provider.CLAUDE,
                    id -> {
                      throw new IllegalStateException("kaboom");
                    }),
            Provider.CHATGPT, adapter(Provider.CHATGPT, ComparisonServiceTest::ok)));

    Map<Provider, Outcome> outcomes = outcomesByProvider(providers, service(45_000));

    assertThat(outcomes.get(Provider.CLAUDE)).isEqualTo(Outcome.ERROR);
    assertThat(outcomes.get(Provider.CHATGPT)).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void slowProviderTimesOutWhileOthersUnaffected() {
    List<Provider> providers = List.of(Provider.CLAUDE, Provider.CHATGPT);
    register(
        Map.of(
            Provider.CLAUDE,
                adapter(
                    Provider.CLAUDE,
                    id -> {
                      sleep(500);
                      return ok(id);
                    }),
            Provider.CHATGPT, adapter(Provider.CHATGPT, ComparisonServiceTest::ok)));

    Map<Provider, Outcome> outcomes = outcomesByProvider(providers, service(100));

    assertThat(outcomes.get(Provider.CLAUDE)).isEqualTo(Outcome.TIMEOUT);
    assertThat(outcomes.get(Provider.CHATGPT)).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void allProvidersFailIndependently() {
    List<Provider> providers = List.of(Provider.GROK, Provider.DEEPSEEK);
    register(
        Map.of(
            Provider.GROK,
                adapter(
                    Provider.GROK,
                    id -> {
                      throw new IllegalStateException("a");
                    }),
            Provider.DEEPSEEK,
                adapter(
                    Provider.DEEPSEEK,
                    id -> {
                      throw new IllegalStateException("b");
                    })));

    List<ProviderResponse> responses =
        service(45_000)
            .fanOut(providers, provider -> new PromptRequest("hi"), (p, t) -> {}, r -> {});

    assertThat(responses).extracting(ProviderResponse::outcome).containsOnly(Outcome.ERROR);
  }

  @Test
  void executePersistsResultsAndMarksComplete() {
    User user = new User("alice", "hash");
    Comparison comparison =
        new Comparison(
            user, "prompt", List.of(Provider.CLAUDE), Map.of(Provider.CLAUDE, "claude-chosen"));
    when(comparisons.findById("c1")).thenReturn(Optional.of(comparison));
    register(Map.of(Provider.CLAUDE, adapter(Provider.CLAUDE, ComparisonServiceTest::ok)));
    List<ResultEvent> events = new ArrayList<>();

    int completed = service(45_000).execute("c1", (p, t) -> {}, events::add);

    assertThat(completed).isEqualTo(1);
    assertThat(events).hasSize(1);
    assertThat(comparison.getStatus()).isEqualTo(Status.COMPLETE);
    assertThat(comparison.getResults())
        .extracting(ProviderResult::getOutcome)
        .containsExactly(Outcome.SUCCESS);
  }

  @Test
  void executePersistsTelemetryAndEmitsItOnTheResultEvent() {
    User user = new User("alice", "hash");
    Comparison comparison =
        new Comparison(
            user, "prompt", List.of(Provider.CLAUDE), Map.of(Provider.CLAUDE, "claude-chosen"));
    when(comparisons.findById("c1")).thenReturn(Optional.of(comparison));
    register(Map.of(Provider.CLAUDE, adapter(Provider.CLAUDE, ComparisonServiceTest::ok)));
    List<ResultEvent> events = new ArrayList<>();

    service(45_000).execute("c1", (p, t) -> {}, events::add);

    ProviderResult persisted = comparison.getResults().get(0);
    assertThat(persisted.getFirstTokenMs()).isEqualTo(3L);
    assertThat(persisted.getInputTokens()).isEqualTo(7L);
    assertThat(persisted.getOutputTokens()).isEqualTo(11L);
    assertThat(persisted.getModel()).isEqualTo("model-" + Provider.CLAUDE);

    ResultEvent event = events.get(0);
    assertThat(event.firstTokenMs()).isEqualTo(3L);
    assertThat(event.inputTokens()).isEqualTo(7L);
    assertThat(event.outputTokens()).isEqualTo(11L);
    assertThat(event.model()).isEqualTo("model-" + Provider.CLAUDE);
  }

  @Test
  void executeDispatchesThePersistedModelPerProvider() {
    User user = new User("alice", "hash");
    Comparison comparison =
        new Comparison(
            user, "prompt", List.of(Provider.CLAUDE), Map.of(Provider.CLAUDE, "claude-haiku-4-5"));
    when(comparisons.findById("c1")).thenReturn(Optional.of(comparison));
    Map<Provider, PromptRequest> seen = new ConcurrentHashMap<>();
    register(Map.of(Provider.CLAUDE, capturing(Provider.CLAUDE, seen)));

    service(45_000).execute("c1", (p, t) -> {}, event -> {});

    assertThat(seen.get(Provider.CLAUDE).model()).isEqualTo("claude-haiku-4-5");
    assertThat(seen.get(Provider.CLAUDE).prompt()).isEqualTo("prompt");
  }

  /**
   * Legacy guard (FR-020): a provider with no persisted model (rows that predate model selection)
   * is never dispatched — it gets its own {@code no_model_recorded} ERROR result, emitted and
   * persisted alongside the others, which proceed normally.
   */
  @Test
  void executeNeverDispatchesAProviderWithoutAPersistedModel() {
    User user = new User("alice", "hash");
    // CLAUDE has a persisted model choice; GEMINI predates model selection (no entry).
    Comparison comparison =
        new Comparison(
            user,
            "prompt",
            List.of(Provider.CLAUDE, Provider.GEMINI),
            Map.of(Provider.CLAUDE, "claude-haiku-4-5"));
    when(comparisons.findById("c1")).thenReturn(Optional.of(comparison));
    Map<Provider, PromptRequest> seen = new ConcurrentHashMap<>();
    register(Map.of(Provider.CLAUDE, capturing(Provider.CLAUDE, seen)));
    List<ResultEvent> events = new CopyOnWriteArrayList<>();

    int completed = service(45_000).execute("c1", (p, t) -> {}, events::add);

    // GEMINI's adapter was never looked up or called; CLAUDE ran its chosen model.
    verify(registry, never()).get(Provider.GEMINI);
    assertThat(seen.keySet()).containsExactly(Provider.CLAUDE);
    // Both providers still report — GEMINI as its own error — and both are persisted.
    assertThat(completed).isEqualTo(2);
    ResultEvent gemini =
        events.stream()
            .filter(event -> event.provider() == Provider.GEMINI)
            .findFirst()
            .orElseThrow();
    assertThat(gemini.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(gemini.errorMessage()).isEqualTo("no_model_recorded");
    assertThat(comparison.getStatus()).isEqualTo(Status.COMPLETE);
    assertThat(comparison.getResults())
        .extracting(ProviderResult::getProvider, ProviderResult::getOutcome)
        .containsExactlyInAnyOrder(
            tuple(Provider.GEMINI, Outcome.ERROR), tuple(Provider.CLAUDE, Outcome.SUCCESS));
  }

  /** An adapter that records the exact {@link PromptRequest} it was dispatched with. */
  private static LlmProvider capturing(Provider id, Map<Provider, PromptRequest> seen) {
    return new LlmProvider() {
      @Override
      public Provider id() {
        return id;
      }

      @Override
      public ProviderResponse stream(PromptRequest request, java.util.function.Consumer<String> t) {
        seen.put(id, request);
        return ok(id);
      }
    };
  }

  @Test
  void executeThrowsWhenComparisonMissing() {
    when(comparisons.findById("gone")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service(45_000).execute("gone", (p, t) -> {}, e -> {}))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void replayEmitsPersistedResultsIncludingTelemetry() {
    User user = new User("alice", "hash");
    Comparison comparison = new Comparison(user, "prompt", List.of(Provider.CLAUDE));
    comparison.addResult(
        new ProviderResult(
            Provider.CLAUDE, Outcome.SUCCESS, "answer", null, 12L, 4L, 8L, 15L, "model-x"));
    comparison.addResult(
        new ProviderResult(Provider.GEMINI, Outcome.ERROR, null, "rate_limited", null));
    when(comparisons.findById("c1")).thenReturn(Optional.of(comparison));
    List<ResultEvent> events = new ArrayList<>();

    int replayed = service(45_000).replay("c1", events::add);

    assertThat(replayed).isEqualTo(2);
    assertThat(events)
        .extracting(ResultEvent::provider)
        .containsExactly(Provider.CLAUDE, Provider.GEMINI);
    assertThat(events.get(0).firstTokenMs()).isEqualTo(4L);
    assertThat(events.get(0).inputTokens()).isEqualTo(8L);
    assertThat(events.get(0).outputTokens()).isEqualTo(15L);
    assertThat(events.get(0).model()).isEqualTo("model-x");
    assertThat(events.get(1).firstTokenMs()).isNull();
    assertThat(events.get(1).model()).isNull();
  }

  private Map<Provider, Outcome> outcomesByProvider(
      List<Provider> providers, ComparisonService service) {
    Map<Provider, Outcome> outcomes = new ConcurrentHashMap<>();
    service.fanOut(
        providers,
        provider -> new PromptRequest("hi"),
        (p, t) -> {},
        response -> outcomes.put(response.provider(), response.outcome()));
    return outcomes;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
