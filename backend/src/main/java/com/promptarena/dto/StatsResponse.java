package com.promptarena.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/comparisons/stats}. One entry per provider the caller has at
 * least one recorded result for, fastest average response first; no history yields an empty list
 * (FR-023).
 */
public record StatsResponse(List<ProviderStats> stats) {}
