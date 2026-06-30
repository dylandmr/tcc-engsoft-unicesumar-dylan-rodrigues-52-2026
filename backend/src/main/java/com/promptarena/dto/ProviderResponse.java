package com.promptarena.dto;

import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;

/**
 * A provider's uniform answer, returned by every {@link com.promptarena.provider.LlmProvider}
 * adapter. No SDK types leak past this boundary (Constitution II). Pure data carrier — outcome
 * classification (SUCCESS vs EMPTY vs ERROR vs TIMEOUT) is performed by the orchestrator/mapper.
 */
public record ProviderResponse(
    Provider provider, Outcome outcome, String text, String errorMessage, Long responseTimeMs) {}
