package com.promptarena.comparison;

import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.dto.ResultEvent;
import com.promptarena.model.Comparison;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderRegistry;
import com.promptarena.provider.ProviderResultMapper;
import com.promptarena.repository.ComparisonRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the concurrent provider fan-out and persists results. Each provider call runs on a
 * virtual thread with its own timeout and is wrapped so that one provider's failure, timeout, or
 * slow response never blocks, fails, or delays the others (FR-010, FR-011, FR-012; research
 * Decision 2). Every future resolves to a {@link ProviderResponse}; none ever propagates.
 */
@Service
public class ComparisonService {

  private final ProviderRegistry registry;
  private final ExecutorService providerExecutor;
  private final ComparisonRepository comparisons;
  private final long timeoutMs;

  public ComparisonService(
      ProviderRegistry registry,
      ExecutorService providerExecutor,
      ComparisonRepository comparisons,
      @Value("${prompt-arena.provider-timeout-ms:45000}") long timeoutMs) {
    this.registry = registry;
    this.providerExecutor = providerExecutor;
    this.comparisons = comparisons;
    this.timeoutMs = timeoutMs;
  }

  /**
   * Run the fan-out for a {@code PENDING} comparison: dispatch every selected provider, emit each
   * {@link ResultEvent} as it arrives, then persist the results and mark the comparison {@code
   * COMPLETE}. Returns the number of providers reported.
   */
  @Transactional
  public int execute(String comparisonId, Consumer<ResultEvent> onResult) {
    Comparison comparison = load(comparisonId);
    PromptRequest request = new PromptRequest(comparison.getPrompt());
    List<ProviderResponse> responses =
        fanOut(comparison.getProviders(), request, response -> onResult.accept(toEvent(response)));
    for (ProviderResponse response : responses) {
      comparison.addResult(
          new ProviderResult(
              response.provider(),
              response.outcome(),
              response.text(),
              response.errorMessage(),
              response.responseTimeMs()));
    }
    comparison.markComplete();
    comparisons.save(comparison);
    return responses.size();
  }

  /**
   * Replay a {@code COMPLETE} comparison's persisted results as {@link ResultEvent}s without
   * calling any provider again (idempotent stream re-open, e.g. from history).
   */
  @Transactional(readOnly = true)
  public int replay(String comparisonId, Consumer<ResultEvent> onResult) {
    Comparison comparison = load(comparisonId);
    List<ProviderResult> results = comparison.getResults();
    for (ProviderResult result : results) {
      onResult.accept(toEvent(result));
    }
    return results.size();
  }

  private Comparison load(String comparisonId) {
    return comparisons
        .findById(comparisonId)
        .orElseThrow(() -> new NoSuchElementException("comparison gone: " + comparisonId));
  }

  /**
   * Fan out the prompt to every provider concurrently, isolating each. {@code onResult} fires once
   * per provider as it completes; the returned list is the aggregated set once all have reported.
   */
  List<ProviderResponse> fanOut(
      List<Provider> providers, PromptRequest request, Consumer<ProviderResponse> onResult) {
    List<CompletableFuture<ProviderResponse>> futures =
        providers.stream().map(provider -> dispatch(provider, request, onResult)).toList();
    return futures.stream().map(CompletableFuture::join).toList();
  }

  private CompletableFuture<ProviderResponse> dispatch(
      Provider provider, PromptRequest request, Consumer<ProviderResponse> onResult) {
    LlmProvider adapter = registry.get(provider);
    return CompletableFuture.supplyAsync(() -> adapter.complete(request), providerExecutor)
        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> classifyFailure(provider, ex))
        .whenComplete((response, ignored) -> onResult.accept(response));
  }

  private static ProviderResponse classifyFailure(Provider provider, Throwable ex) {
    Throwable cause = unwrap(ex);
    if (cause instanceof TimeoutException) {
      return ProviderResultMapper.timeout(provider);
    }
    return ProviderResultMapper.error(provider, cause.getMessage(), null);
  }

  private static Throwable unwrap(Throwable ex) {
    Throwable cause = ex.getCause();
    return cause != null ? cause : ex;
  }

  private static ResultEvent toEvent(ProviderResponse response) {
    return new ResultEvent(
        response.provider(),
        response.outcome(),
        response.text(),
        response.errorMessage(),
        response.responseTimeMs());
  }

  private static ResultEvent toEvent(ProviderResult result) {
    return new ResultEvent(
        result.getProvider(),
        result.getOutcome(),
        result.getResponseText(),
        result.getErrorMessage(),
        result.getResponseTimeMs());
  }
}
