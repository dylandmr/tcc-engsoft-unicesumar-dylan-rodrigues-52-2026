package com.promptarena.dto;

import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;

/**
 * Payload of an SSE {@code result} event (and of each entry in a comparison-detail response). One
 * per provider. For {@code ERROR}/{@code TIMEOUT}, {@code errorMessage} is set and {@code
 * responseText} is null; {@code responseTimeMs} is null when no latency was measured. The telemetry
 * fields ({@code firstTokenMs}, {@code inputTokens}, {@code outputTokens}, {@code model} — FR-019)
 * are nullable and only present when the provider reported them.
 */
public record ResultEvent(
    Provider provider,
    Outcome outcome,
    String responseText,
    String errorMessage,
    Long responseTimeMs,
    Long firstTokenMs,
    Long inputTokens,
    Long outputTokens,
    String model) {}
