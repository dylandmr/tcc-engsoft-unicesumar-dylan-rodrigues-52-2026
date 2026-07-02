package com.promptarena.dto;

import java.util.List;

/** Response body for {@code GET /api/comparisons}. Empty history yields an empty list (FR-017). */
public record ComparisonListResponse(List<ComparisonSummary> comparisons) {}
