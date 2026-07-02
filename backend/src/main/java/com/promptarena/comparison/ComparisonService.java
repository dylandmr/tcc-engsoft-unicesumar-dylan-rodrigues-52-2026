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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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
  public int execute(
      String comparisonId, BiConsumer<Provider, String> onToken, Consumer<ResultEvent> onResult) {
    Comparison comparison = load(comparisonId);
    // Each provider runs the model persisted at POST time (FR-020); providers with no entry
    // (comparisons that predate model selection) get null and fall back to the adapter default.
    Map<Provider, String> models = comparison.getModels();
    List<ProviderResponse> responses =
        fanOut(
            comparison.getProviders(),
            provider -> new PromptRequest(comparison.getPrompt(), models.get(provider)),
            onToken,
            response -> onResult.accept(toEvent(response)));
    for (ProviderResponse response : responses) {
      comparison.addResult(
          new ProviderResult(
              response.provider(),
              response.outcome(),
              response.text(),
              response.errorMessage(),
              response.responseTimeMs(),
              response.firstTokenMs(),
              response.inputTokens(),
              response.outputTokens(),
              response.model()));
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
   * Fan out the prompt to every provider concurrently, isolating each. {@code requestFor} builds
   * each provider's request (same prompt, per-provider model — FR-020). {@code onResult} fires once
   * per provider as it completes; the returned list is the aggregated set once all have reported.
   */
  List<ProviderResponse> fanOut(
      List<Provider> providers,
      Function<Provider, PromptRequest> requestFor,
      BiConsumer<Provider, String> onToken,
      Consumer<ProviderResponse> onResult) {
    List<CompletableFuture<ProviderResponse>> futures =
        providers.stream()
            .map(provider -> dispatch(provider, requestFor.apply(provider), onToken, onResult))
            .toList();
    return futures.stream().map(CompletableFuture::join).toList();
  }

  private CompletableFuture<ProviderResponse> dispatch(
      Provider provider,
      PromptRequest request,
      BiConsumer<Provider, String> onToken,
      Consumer<ProviderResponse> onResult) {
    LlmProvider adapter = registry.get(provider);
    return CompletableFuture.supplyAsync(
            () -> adapter.stream(request, token -> onToken.accept(provider, token)),
            providerExecutor)
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
        response.responseTimeMs(),
        response.firstTokenMs(),
        response.inputTokens(),
        response.outputTokens(),
        response.model());
  }

  private static ResultEvent toEvent(ProviderResult result) {
    return new ResultEvent(
        result.getProvider(),
        result.getOutcome(),
        result.getResponseText(),
        result.getErrorMessage(),
        result.getResponseTimeMs(),
        result.getFirstTokenMs(),
        result.getInputTokens(),
        result.getOutputTokens(),
        result.getModel());
  }
}
