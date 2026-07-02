package com.promptarena.provider;

import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Provider;
import java.util.List;
import java.util.function.Consumer;

/**
 * Uniform interface to every LLM provider. Provider-specific SDK details MUST NOT leak past
 * implementations of this interface (Constitution II — Provider Abstraction).
 */
public interface LlmProvider {

  /** Which provider this adapter serves. */
  Provider id();

  /**
   * Stream the prompt to the provider, invoking {@code onToken} for each incremental text delta as
   * it arrives, then returning the final aggregated answer. Implementations make a blocking call;
   * the orchestrator runs them concurrently on virtual threads with per-call timeouts and isolates
   * failures. Implementations should not throw for provider-side errors — they surface them as that
   * provider's own result.
   */
  ProviderResponse stream(PromptRequest request, Consumer<String> onToken);

  /** Run the call to completion, discarding the intermediate deltas. */
  default ProviderResponse complete(PromptRequest request) {
    return stream(request, token -> {});
  }

  /**
   * The chat-capable model ids this provider's own API reports as available (FR-020). Unlike {@link
   * #stream}, implementations MAY throw on an API failure — the model catalog isolates each
   * provider's fetch and degrades to the curated list. Unconfigured adapters return an empty list.
   */
  default List<String> listModels() {
    return List.of();
  }

  /** The configured default model id, or {@code null} when the adapter has none (unavailable). */
  default String defaultModel() {
    return null;
  }
}
