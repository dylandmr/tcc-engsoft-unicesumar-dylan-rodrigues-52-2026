package com.promptarena.dto;

/**
 * A prompt to send to a single provider. Uniform across all providers (Constitution II). {@code
 * model} is the model chosen for this comparison (FR-020) and is used verbatim on the wire — there
 * is no default anywhere; the orchestrator never dispatches a provider without one (legacy rows
 * that persisted no model are guarded with their own ERROR result).
 */
public record PromptRequest(String prompt, String model) {

  /** Convenience for tests whose stub adapters never read the model. */
  public PromptRequest(String prompt) {
    this(prompt, null);
  }
}
