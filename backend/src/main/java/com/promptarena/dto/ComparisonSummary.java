package com.promptarena.dto;

import com.promptarena.model.Provider;
import java.time.Instant;
import java.util.List;

/** One row in the history list ({@code GET /api/comparisons}). */
public record ComparisonSummary(
    String id, String prompt, List<Provider> providers, Instant createdAt) {}
