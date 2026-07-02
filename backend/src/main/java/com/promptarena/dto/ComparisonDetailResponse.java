package com.promptarena.dto;

import com.promptarena.model.Provider;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response body for {@code GET /api/comparisons/{id}}: the prompt plus each recorded result. {@code
 * models} is the model requested per provider when the comparison was created (FR-020) — empty for
 * comparisons persisted before model selection existed. {@code analysis} is the recorded
 * comparative analysis (FR-021), or null when none has been generated.
 */
public record ComparisonDetailResponse(
    String id,
    String prompt,
    Instant createdAt,
    Map<Provider, String> models,
    List<ResultEvent> results,
    AnalysisSummary analysis) {}
