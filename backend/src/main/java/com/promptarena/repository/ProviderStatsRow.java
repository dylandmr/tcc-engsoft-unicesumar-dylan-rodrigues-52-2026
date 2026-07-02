package com.promptarena.repository;

import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;

/**
 * One provider result reduced to what the FR-023 aggregates need: who ran, how it ended, and the
 * recorded telemetry. Every telemetry field is nullable (FR-019 — a value the provider never
 * reported is absent), which is exactly why the aggregation happens in Java rather than in SQL.
 */
public record ProviderStatsRow(
    Provider provider,
    Outcome outcome,
    Long responseTimeMs,
    Long firstTokenMs,
    Long outputTokens) {}
