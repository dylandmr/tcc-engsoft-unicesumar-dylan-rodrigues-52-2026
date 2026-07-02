package com.promptarena.dto;

import com.promptarena.model.Provider;
import java.util.List;

/** Response body for {@code POST /api/comparisons}: the new id and the echoed provider set. */
public record CreateComparisonResponse(String comparisonId, List<Provider> providers) {}
