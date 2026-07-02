package com.promptarena.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.promptarena.catalog.ModelCatalogService;
import com.promptarena.dto.AnalysisEvent;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderCatalogEntry;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.dto.StreamTelemetry;
import com.promptarena.model.Comparison;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.model.User;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderRegistry;
import com.promptarena.provider.ProviderResultMapper;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.web.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the analysis orchestration (FR-021): eligibility and judge validation (FR-020
 * semantics), the injectable-randomness shuffle behind the anonymous labels, persist-once/replay
 * behavior, and judge-failure isolation (nothing persisted).
 */
@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

  @Mock private ProviderRegistry registry;
  @Mock private ComparisonRepository comparisons;
  @Mock private ModelCatalogService modelCatalog;

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private AnalysisService service(Random random, long timeoutMs) {
    return new AnalysisService(registry, executor, comparisons, modelCatalog, random, timeoutMs);
  }

  private AnalysisService service() {
    return service(new Random(42), 45_000);
  }

  /** A {@link Random} whose {@code nextInt} is always 0: shuffling a pair reverses it. */
  private static Random reversing() {
    return new Random() {
      @Override
      public int nextInt(int bound) {
        return 0;
      }
    };
  }

  /** A COMPLETE comparison with one SUCCESS result per given provider ("answer-<provider>"). */
  private Comparison completeComparison(Provider... successes) {
    Comparison comparison = new Comparison(new User("alice", "hash"), "the prompt", List.of());
    for (Provider provider : successes) {
      comparison.addResult(
          new ProviderResult(provider, Outcome.SUCCESS, "answer-" + provider, null, 10L));
    }
    comparison.markComplete();
    when(comparisons.findById("c1")).thenReturn(Optional.of(comparison));
    return comparison;
  }

  private void stubCatalog(Provider provider, List<String> liveModels) {
    when(modelCatalog.catalogFor(List.of(provider)))
        .thenReturn(Map.of(provider, new ProviderCatalogEntry(provider, true, liveModels)));
  }

  /** A judge adapter that streams its answer as one token and returns it. */
  private LlmProvider judgeAnswering(
      Provider id, String answer, AtomicReference<PromptRequest> seen) {
    return new LlmProvider() {
      @Override
      public Provider id() {
        return id;
      }

      @Override
      public ProviderResponse stream(PromptRequest request, Consumer<String> onToken) {
        seen.set(request);
        onToken.accept(answer);
        return ProviderResultMapper.success(id, answer, 10L, StreamTelemetry.none());
      }
    };
  }

  // --- prepare: eligibility ---

  @Test
  void prepareRejectsAComparisonThatIsNotComplete() {
    Comparison pending = new Comparison(new User("alice", "hash"), "p", List.of());
    when(comparisons.findById("c1")).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service().prepare("c1", "CLAUDE", "claude-x"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("comparison_not_complete");
  }

  @Test
  void prepareRejectsFewerThanTwoSuccessfulAnswers() {
    Comparison comparison = completeComparison(Provider.GEMINI);
    comparison.addResult(
        new ProviderResult(Provider.CLAUDE, Outcome.ERROR, null, "rate_limited", null));

    assertThatThrownBy(() -> service().prepare("c1", "CLAUDE", "claude-x"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("insufficient_results");
  }

  @Test
  void prepareThrowsWhenTheComparisonIsGone() {
    when(comparisons.findById("gone")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().prepare("gone", "CLAUDE", "claude-x"))
        .isInstanceOf(NoSuchElementException.class);
  }

  // --- prepare: judge validation (FR-020 semantics) ---

  @Test
  void prepareRejectsAnUnparseableJudgeProvider() {
    completeComparison(Provider.GEMINI, Provider.CLAUDE);

    assertThatThrownBy(() -> service().prepare("c1", "FOO", "claude-x"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("unknown_provider");
  }

  @Test
  void prepareRejectsAnAbsentJudgeProvider() {
    completeComparison(Provider.GEMINI, Provider.CLAUDE);

    assertThatThrownBy(() -> service().prepare("c1", null, "claude-x"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("unknown_provider");
  }

  @Test
  void prepareRejectsAnAbsentOrBlankJudgeModel() {
    completeComparison(Provider.GEMINI, Provider.CLAUDE);

    assertThatThrownBy(() -> service().prepare("c1", "CLAUDE", null))
        .isInstanceOf(ValidationException.class)
        .hasMessage("missing_model");
    assertThatThrownBy(() -> service().prepare("c1", "CLAUDE", "   "))
        .isInstanceOf(ValidationException.class)
        .hasMessage("missing_model");
  }

  @Test
  void prepareRejectsAJudgeModelOutsideTheLiveSet() {
    completeComparison(Provider.GEMINI, Provider.CLAUDE);
    stubCatalog(Provider.CLAUDE, List.of("claude-x"));

    assertThatThrownBy(() -> service().prepare("c1", "CLAUDE", "clod-9000"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("unknown_model");
  }

  /** An unconfigured judge provider has an empty live set, so it fails as unknown_model. */
  @Test
  void prepareRejectsAnUnconfiguredJudgeProviderAsUnknownModel() {
    completeComparison(Provider.GEMINI, Provider.CLAUDE);
    stubCatalog(Provider.GROK, List.of());

    assertThatThrownBy(() -> service().prepare("c1", "GROK", "grok-4"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("unknown_model");
  }

  @Test
  void prepareReturnsTheValidatedJudge() {
    completeComparison(Provider.GEMINI, Provider.CLAUDE);
    stubCatalog(Provider.CHATGPT, List.of("gpt-x", "gpt-y"));

    JudgeSelection judge = service().prepare("c1", "CHATGPT", "gpt-x");

    assertThat(judge).isEqualTo(new JudgeSelection(Provider.CHATGPT, "gpt-x"));
  }

  /** A recorded analysis is replayed: the judge parameters are ignored, even garbage ones. */
  @Test
  void prepareForARecordedAnalysisSignalsReplayAndIgnoresTheJudgeParameters() {
    Comparison comparison = completeComparison(Provider.GEMINI, Provider.CLAUDE);
    comparison.recordAnalysis(
        "texto", Provider.CLAUDE, "claude-x", List.of(Provider.GEMINI, Provider.CLAUDE));

    JudgeSelection judge = service().prepare("c1", "FOO", null);

    assertThat(judge).isNull();
    verifyNoInteractions(modelCatalog);
  }

  // --- run: replay ---

  @Test
  void runReplaysTheRecordedAnalysisWithoutCallingAnyJudge() {
    Comparison comparison = completeComparison(Provider.GEMINI, Provider.CLAUDE);
    comparison.recordAnalysis(
        "as diferenças…", Provider.CHATGPT, "gpt-x", List.of(Provider.CLAUDE, Provider.GEMINI));
    StringBuilder tokens = new StringBuilder();

    // Even a live judge selection (the prepare/run race) must not trigger a second generation.
    AnalysisEvent event =
        service().run("c1", new JudgeSelection(Provider.GROK, "grok-4"), tokens::append);

    assertThat(event.text()).isEqualTo("as diferenças…");
    assertThat(event.errorMessage()).isNull();
    assertThat(event.provider()).isEqualTo(Provider.CHATGPT);
    assertThat(event.model()).isEqualTo("gpt-x");
    assertThat(event.labels())
        .containsExactly(Map.entry("A", Provider.CLAUDE), Map.entry("B", Provider.GEMINI));
    assertThat(tokens).isEmpty();
    verifyNoInteractions(registry);
    verify(comparisons, never()).save(any());
  }

  @Test
  void runThrowsWhenTheComparisonIsGone() {
    when(comparisons.findById("gone")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service().run("gone", new JudgeSelection(Provider.CLAUDE, "claude-x"), token -> {}))
        .isInstanceOf(NoSuchElementException.class);
  }

  // --- run: generation ---

  @Test
  void runShufflesAnonymizesCallsTheJudgeAndPersistsBeforeReturningTheTerminalEvent() {
    Comparison comparison = completeComparison(Provider.GEMINI, Provider.CLAUDE);
    AtomicReference<PromptRequest> seen = new AtomicReference<>();
    when(registry.get(Provider.CHATGPT))
        .thenReturn(judgeAnswering(Provider.CHATGPT, "as diferenças…", seen));
    StringBuilder tokens = new StringBuilder();

    // The always-zero randomness reverses the pair: shuffled order is [CLAUDE, GEMINI].
    AnalysisEvent event =
        service(reversing(), 45_000)
            .run("c1", new JudgeSelection(Provider.CHATGPT, "gpt-x"), tokens::append);

    // The judge ran the chosen model over the anonymized prompt, in the shuffled order.
    assertThat(seen.get().model()).isEqualTo("gpt-x");
    assertThat(seen.get().prompt()).contains("Modelo A:\n\"\"\"\nanswer-CLAUDE\n\"\"\"");
    assertThat(seen.get().prompt()).contains("Modelo B:\n\"\"\"\nanswer-GEMINI\n\"\"\"");
    assertThat(seen.get().prompt()).contains("the prompt");
    assertThat(tokens.toString()).isEqualTo("as diferenças…");
    // Persisted before the terminal event was returned.
    assertThat(comparison.hasAnalysis()).isTrue();
    assertThat(comparison.getAnalysisText()).isEqualTo("as diferenças…");
    assertThat(comparison.getAnalysisProvider()).isEqualTo(Provider.CHATGPT);
    assertThat(comparison.getAnalysisModel()).isEqualTo("gpt-x");
    assertThat(comparison.getAnalysisOrder()).containsExactly(Provider.CLAUDE, Provider.GEMINI);
    verify(comparisons).save(comparison);
    // The terminal event mirrors what was persisted.
    assertThat(event.text()).isEqualTo("as diferenças…");
    assertThat(event.errorMessage()).isNull();
    assertThat(event.provider()).isEqualTo(Provider.CHATGPT);
    assertThat(event.model()).isEqualTo("gpt-x");
    assertThat(event.labels())
        .containsExactly(Map.entry("A", Provider.CLAUDE), Map.entry("B", Provider.GEMINI));
  }

  @Test
  void runShufflesDeterministicallyForTheSameSeed() {
    List<Provider> lanes =
        List.of(Provider.GEMINI, Provider.CHATGPT, Provider.CLAUDE, Provider.GROK);
    AtomicReference<PromptRequest> seen = new AtomicReference<>();
    when(registry.get(Provider.DEEPSEEK))
        .thenReturn(judgeAnswering(Provider.DEEPSEEK, "análise", seen));
    JudgeSelection judge = new JudgeSelection(Provider.DEEPSEEK, "deepseek-chat");

    Comparison first = completeComparison(lanes.toArray(Provider[]::new));
    service(new Random(42), 45_000).run("c1", judge, token -> {});
    List<Provider> firstOrder = List.copyOf(first.getAnalysisOrder());

    Comparison second = completeComparison(lanes.toArray(Provider[]::new));
    service(new Random(42), 45_000).run("c1", judge, token -> {});

    assertThat(second.getAnalysisOrder()).isEqualTo(firstOrder);
    assertThat(firstOrder).containsExactlyInAnyOrderElementsOf(lanes);
  }

  /** A successful-but-empty judge answer (EMPTY) is still recorded, like any completed call. */
  @Test
  void runRecordsAnEmptyJudgeAnswer() {
    Comparison comparison = completeComparison(Provider.GEMINI, Provider.CLAUDE);
    AtomicReference<PromptRequest> seen = new AtomicReference<>();
    when(registry.get(Provider.CHATGPT)).thenReturn(judgeAnswering(Provider.CHATGPT, "", seen));

    AnalysisEvent event =
        service().run("c1", new JudgeSelection(Provider.CHATGPT, "gpt-x"), token -> {});

    assertThat(comparison.hasAnalysis()).isTrue();
    assertThat(comparison.getAnalysisText()).isEmpty();
    assertThat(event.text()).isEmpty();
    assertThat(event.errorMessage()).isNull();
    verify(comparisons).save(comparison);
  }

  // --- run: judge failure taints nothing ---

  @Test
  void runJudgeErrorPersistsNothingAndSurfacesTheMessage() {
    Comparison comparison = completeComparison(Provider.GEMINI, Provider.CLAUDE);
    when(registry.get(Provider.CHATGPT))
        .thenReturn(
            new LlmProvider() {
              @Override
              public Provider id() {
                return Provider.CHATGPT;
              }

              @Override
              public ProviderResponse stream(PromptRequest request, Consumer<String> onToken) {
                throw new IllegalStateException("kaboom");
              }
            });

    AnalysisEvent event =
        service().run("c1", new JudgeSelection(Provider.CHATGPT, "gpt-x"), token -> {});

    assertThat(event.text()).isNull();
    assertThat(event.errorMessage()).isEqualTo("kaboom");
    assertThat(event.provider()).isEqualTo(Provider.CHATGPT);
    assertThat(event.model()).isEqualTo("gpt-x");
    assertThat(event.labels()).hasSize(2);
    assertThat(comparison.hasAnalysis()).isFalse();
    verify(comparisons, never()).save(any());
  }

  @Test
  void runJudgeTimeoutPersistsNothingAndSurfacesTheTimeoutMessage() {
    Comparison comparison = completeComparison(Provider.GEMINI, Provider.CLAUDE);
    when(registry.get(Provider.CHATGPT))
        .thenReturn(
            new LlmProvider() {
              @Override
              public Provider id() {
                return Provider.CHATGPT;
              }

              @Override
              public ProviderResponse stream(PromptRequest request, Consumer<String> onToken) {
                try {
                  Thread.sleep(500);
                } catch (InterruptedException ex) {
                  Thread.currentThread().interrupt();
                }
                return ProviderResultMapper.success(
                    Provider.CHATGPT, "too late", 500L, StreamTelemetry.none());
              }
            });

    AnalysisEvent event =
        service(new Random(42), 100)
            .run("c1", new JudgeSelection(Provider.CHATGPT, "gpt-x"), token -> {});

    assertThat(event.text()).isNull();
    assertThat(event.errorMessage()).isEqualTo("Sem resposta dentro do tempo limite.");
    assertThat(comparison.hasAnalysis()).isFalse();
    verify(comparisons, never()).save(any());
  }
}
