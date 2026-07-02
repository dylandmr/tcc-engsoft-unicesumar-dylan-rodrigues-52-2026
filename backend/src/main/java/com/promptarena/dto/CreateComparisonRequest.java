package com.promptarena.dto;

import java.util.List;
import java.util.Map;

/**
 * Request body for {@code POST /api/comparisons}. Provider names are accepted as raw strings and
 * validated/parsed by the comparison layer so unknown values surface a machine-readable error
 * (rather than a Jackson deserialization failure). {@code models} (optional, FR-020) maps a
 * selected provider name to the model id that should answer; providers with no entry run their
 * default model.
 */
public record CreateComparisonRequest(
    String prompt, List<String> providers, Map<String, String> models) {}
