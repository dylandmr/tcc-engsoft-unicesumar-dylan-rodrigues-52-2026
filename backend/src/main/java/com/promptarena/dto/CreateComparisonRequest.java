package com.promptarena.dto;

import java.util.List;

/**
 * Request body for {@code POST /api/comparisons}. Provider names are accepted as raw strings and
 * validated/parsed by the comparison layer so unknown values surface a machine-readable error
 * (rather than a Jackson deserialization failure).
 */
public record CreateComparisonRequest(String prompt, List<String> providers) {}
