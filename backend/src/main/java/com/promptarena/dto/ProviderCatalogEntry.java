package com.promptarena.dto;

import com.promptarena.model.Provider;
import java.util.List;

/**
 * One provider's entry in {@code GET /api/providers} (FR-020): configuration state and the models
 * selectable for it. {@code models} is exactly what the provider's own API reports as available,
 * sorted ascending and deduplicated — the system defines no default, curated, or hardcoded model
 * choices. It is empty when the provider is unconfigured or its live fetch failed/timed out; such a
 * provider cannot be part of a comparison.
 */
public record ProviderCatalogEntry(Provider provider, boolean configured, List<String> models) {}
