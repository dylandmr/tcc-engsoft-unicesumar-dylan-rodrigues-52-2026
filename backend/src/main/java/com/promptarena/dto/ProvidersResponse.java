package com.promptarena.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/providers}: every supported provider in canonical order
 * (GEMINI, CHATGPT, CLAUDE, GROK, DEEPSEEK), each with its selectable models (FR-020).
 */
public record ProvidersResponse(List<ProviderCatalogEntry> providers) {}
