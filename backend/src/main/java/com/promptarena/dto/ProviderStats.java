package com.promptarena.dto;

import com.promptarena.model.Provider;

/**
 * One provider's aggregate entry in the stats response (FR-023). {@code runs} always equals {@code
 * successes + empties + errors + timeouts}; {@code telemetryRuns} counts the runs whose telemetry
 * was recorded ({@code responseTimeMs} present, FR-019) — the honest basis the SPA captions. Each
 * average is computed only over the runs that carry the values it needs and is null when none do.
 * {@code avgTokensPerSecond} is the raw per-run-rate mean — the SPA formats it to one decimal.
 */
public record ProviderStats(
    Provider provider,
    long runs,
    long successes,
    long empties,
    long errors,
    long timeouts,
    long telemetryRuns,
    Long avgResponseTimeMs,
    Long avgFirstTokenMs,
    Double avgTokensPerSecond) {}
