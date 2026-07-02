package com.promptarena.dto;

import com.promptarena.model.Provider;

/**
 * Static, configuration-derived facts about one provider, resolved once at wiring time (FR-018,
 * FR-020): whether a server-side API key is present and which model is the default (the env
 * override when set, otherwise the adapter's constant). Built in {@code ProviderConfig} next to the
 * adapter wiring so the default is correct even for unconfigured providers.
 */
public record ProviderDescriptor(Provider provider, boolean configured, String defaultModel) {}
