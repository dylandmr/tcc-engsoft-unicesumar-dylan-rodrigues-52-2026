package com.promptarena.dto;

import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;

/**
 * A provider's uniform answer, returned by every {@link com.promptarena.provider.LlmProvider}
 * adapter. No SDK types leak past this boundary (Constitution II). Pure data carrier — outcome
 * classification (SUCCESS vs EMPTY vs ERROR vs TIMEOUT) is performed by the orchestrator/mapper.
 * The telemetry fields ({@code firstTokenMs}, {@code inputTokens}, {@code outputTokens}, {@code
 * model} — FR-019) are nullable and only populated on successful streams.
 */
public record ProviderResponse(
    Provider provider,
    Outcome outcome,
    String text,
    String errorMessage,
    Long responseTimeMs,
    Long firstTokenMs,
    Long inputTokens,
    Long outputTokens,
    String model) {}
