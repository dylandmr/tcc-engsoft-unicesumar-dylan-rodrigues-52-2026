package com.promptarena.dto;

import com.promptarena.model.Provider;

/**
 * Static, configuration-derived facts about one provider, resolved once at wiring time (FR-018,
 * FR-020): whether a server-side API key is present. The system defines no default model — the user
 * chooses one per comparison from the provider's live catalog.
 */
public record ProviderDescriptor(Provider provider, boolean configured) {}
