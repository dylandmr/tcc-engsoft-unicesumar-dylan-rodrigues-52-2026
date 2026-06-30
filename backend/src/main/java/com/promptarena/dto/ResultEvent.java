package com.promptarena.dto;

import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;

/**
 * Payload of an SSE {@code result} event (and of each entry in a comparison-detail response). One
 * per provider. For {@code ERROR}/{@code TIMEOUT}, {@code errorMessage} is set and {@code
 * responseText} is null; {@code responseTimeMs} is null when no latency was measured.
 */
public record ResultEvent(
    Provider provider,
    Outcome outcome,
    String responseText,
    String errorMessage,
    Long responseTimeMs) {}
