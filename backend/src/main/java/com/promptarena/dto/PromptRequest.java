package com.promptarena.dto;

/**
 * A prompt to send to a single provider. Uniform across all providers (Constitution II). {@code
 * model} is the per-comparison model choice (FR-020); when {@code null} the adapter falls back to
 * its configured default (pre-feature comparisons persisted no model).
 */
public record PromptRequest(String prompt, String model) {

  /** A prompt with no explicit model — the adapter's configured default answers. */
  public PromptRequest(String prompt) {
    this(prompt, null);
  }
}
