package com.promptarena.comparison;

import com.promptarena.catalog.ModelCatalogService;
import com.promptarena.dto.AnalysisEvent;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Comparison;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.model.Status;
import com.promptarena.provider.ProviderRegistry;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.web.ValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the on-demand comparative analysis (FR-021): one judge call over the comparison's
 * successful answers, anonymized as "Modelo A/B/…" in an order shuffled with injectable randomness.
 * The judge reuses the uniform {@link com.promptarena.provider.LlmProvider} fan-out machinery (same
 * executor, timeout, and failure classification as the results fan-out), so no SDK detail leaks
 * here. A successful analysis is persisted once and replayed forever after; a judge failure is
 * reported on the stream only and persists nothing.
 */
@Service
public class AnalysisService {

  /** An analysis needs at least two successful answers to compare (FR-021). */
  private static final int MIN_SUCCESSES = 2;

  private final ProviderRegistry registry;
  private final ExecutorService providerExecutor;
  private final ComparisonRepository comparisons;
  private final ModelCatalogService modelCatalog;
  private final Random random;
  private final long timeoutMs;

  public AnalysisService(
      ProviderRegistry registry,
      ExecutorService providerExecutor,
      ComparisonRepository comparisons,
      ModelCatalogService modelCatalog,
      Random random,
      @Value("${prompt-arena.provider-timeout-ms:45000}") long timeoutMs) {
    this.registry = registry;
    this.providerExecutor = providerExecutor;
    this.comparisons = comparisons;
    this.modelCatalog = modelCatalog;
    this.random = random;
    this.timeoutMs = timeoutMs;
  }

  /**
   * Validate an analysis request before any SSE is emitted. Returns the validated judge choice for
   * a generation, or {@code null} when a recorded analysis will be replayed (judge parameters are
   * then ignored per the contract). Throws a {@link ValidationException} carrying the contract's
   * machine-readable code otherwise: {@code comparison_not_complete}, {@code insufficient_results},
   * or — FR-020 semantics for the judge — {@code unknown_provider} / {@code missing_model} / {@code
   * unknown_model}. An unconfigured judge provider has an empty live model set, so it naturally
   * fails as {@code unknown_model}.
   */
  @Transactional(readOnly = true)
  public JudgeSelection prepare(String comparisonId, String judgeProvider, String judgeModel) {
    Comparison comparison = load(comparisonId);
    if (comparison.getStatus() != Status.COMPLETE) {
      throw new ValidationException("comparison_not_complete");
    }
    if (comparison.hasAnalysis()) {
      return null;
    }
    if (successResults(comparison).size() < MIN_SUCCESSES) {
      throw new ValidationException("insufficient_results");
    }
    Provider provider = parseJudgeProvider(judgeProvider);
    if (judgeModel == null || judgeModel.isBlank()) {
      throw new ValidationException("missing_model");
    }
    List<String> liveModels = modelCatalog.catalogFor(List.of(provider)).get(provider).models();
    if (!liveModels.contains(judgeModel)) {
      throw new ValidationException("unknown_model");
    }
    return new JudgeSelection(provider, judgeModel);
  }

  /**
   * Run one analysis stream to its terminal event. A recorded analysis is replayed as-is (no judge
   * call, no chunks — also covers the race where another stream recorded one since {@link
   * #prepare}). Otherwise the successful answers are shuffled, the judge is called with each delta
   * forwarded to {@code onToken}, and — on a SUCCESS/EMPTY outcome — the analysis is persisted
   * before the terminal event is returned. A judge ERROR/TIMEOUT persists nothing and surfaces as
   * the terminal event's {@code errorMessage}.
   */
  @Transactional
  public AnalysisEvent run(String comparisonId, JudgeSelection judge, Consumer<String> onToken) {
    Comparison comparison = load(comparisonId);
    if (comparison.hasAnalysis()) {
      return recordedEvent(comparison);
    }
    List<ProviderResult> shuffled = new ArrayList<>(successResults(comparison));
    Collections.shuffle(shuffled, random);
    List<Provider> order = shuffled.stream().map(ProviderResult::getProvider).toList();
    String judgePrompt =
        JudgePromptBuilder.build(
            comparison.getPrompt(),
            shuffled.stream().map(ProviderResult::getResponseText).toList());
    ProviderResponse response = callJudge(judge, judgePrompt, onToken);
    if (response.outcome() == Outcome.ERROR || response.outcome() == Outcome.TIMEOUT) {
      return new AnalysisEvent(
          null, response.errorMessage(), judge.provider(), judge.model(), labels(order));
    }
    comparison.recordAnalysis(response.text(), judge.provider(), judge.model(), order);
    comparisons.save(comparison);
    return recordedEvent(comparison);
  }

  /** The terminal {@code analysis} event for a comparison's recorded analysis. */
  static AnalysisEvent recordedEvent(Comparison comparison) {
    return new AnalysisEvent(
        comparison.getAnalysisText(),
        null,
        comparison.getAnalysisProvider(),
        comparison.getAnalysisModel(),
        labels(comparison.getAnalysisOrder()));
  }

  /** The label→provider mapping behind the anonymous order: index 0 → "A", index 1 → "B", … */
  static Map<String, Provider> labels(List<Provider> order) {
    Map<String, Provider> labels = new LinkedHashMap<>();
    for (int i = 0; i < order.size(); i++) {
      labels.put(JudgePromptBuilder.label(i), order.get(i));
    }
    return labels;
  }

  /**
   * The single judge call, isolated exactly like the results fan-out: on the provider executor,
   * bounded by the per-provider timeout, with failures classified into the uniform response shape
   * (never thrown).
   */
  private ProviderResponse callJudge(
      JudgeSelection judge, String judgePrompt, Consumer<String> onToken) {
    return CompletableFuture.supplyAsync(
            () ->
                registry.get(judge.provider()).stream(
                    new PromptRequest(judgePrompt, judge.model()), onToken),
            providerExecutor)
        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> ComparisonService.classifyFailure(judge.provider(), ex))
        .join();
  }

  private static List<ProviderResult> successResults(Comparison comparison) {
    return comparison.getResults().stream()
        .filter(result -> result.getOutcome() == Outcome.SUCCESS)
        .toList();
  }

  private static Provider parseJudgeProvider(String name) {
    try {
      return Provider.valueOf(name);
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new ValidationException("unknown_provider");
    }
  }

  private Comparison load(String comparisonId) {
    return comparisons
        .findById(comparisonId)
        .orElseThrow(() -> new NoSuchElementException("comparison gone: " + comparisonId));
  }
}
