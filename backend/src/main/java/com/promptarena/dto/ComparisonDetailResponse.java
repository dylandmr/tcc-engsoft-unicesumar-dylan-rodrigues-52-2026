package com.promptarena.dto;

import java.time.Instant;
import java.util.List;

/** Response body for {@code GET /api/comparisons/{id}}: the prompt plus each recorded result. */
public record ComparisonDetailResponse(
    String id, String prompt, Instant createdAt, List<ResultEvent> results) {}
