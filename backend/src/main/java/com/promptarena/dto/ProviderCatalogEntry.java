package com.promptarena.dto;

import com.promptarena.model.Provider;
import java.util.List;

/**
 * One provider's entry in {@code GET /api/providers} (FR-020): configuration state, default model,
 * and the models selectable for it. {@code models} is the sorted, deduplicated union of the curated
 * list, the provider-reported live list, and the default model; {@code source} is {@code "live"}
 * when the provider's own list API contributed entries, {@code "curated"} otherwise.
 */
public record ProviderCatalogEntry(
    Provider provider,
    boolean configured,
    String defaultModel,
    List<String> models,
    String source) {}
