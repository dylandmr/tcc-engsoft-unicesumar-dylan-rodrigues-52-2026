package com.promptarena.provider;

import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Provider;

/**
 * Uniform interface to every LLM provider. Provider-specific SDK details MUST NOT leak past
 * implementations of this interface (Constitution II — Provider Abstraction).
 */
public interface LlmProvider {

  /** Which provider this adapter serves. */
  Provider id();

  /**
   * Send the prompt to the provider and return its answer. Implementations make a blocking call;
   * the orchestrator runs them concurrently on virtual threads with per-call timeouts and isolates
   * failures. Implementations should not throw for provider-side errors — they should be surfaced
   * by the orchestrator as that provider's own result.
   */
  ProviderResponse complete(PromptRequest request);
}
