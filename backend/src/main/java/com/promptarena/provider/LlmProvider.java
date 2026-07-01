package com.promptarena.provider;

import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Provider;
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
}
