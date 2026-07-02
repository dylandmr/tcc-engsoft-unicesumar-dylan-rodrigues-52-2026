package com.promptarena.dto;

import com.promptarena.model.Provider;
import java.util.Map;

/**
 * The recorded comparative analysis (FR-021) as carried by the comparison-detail response: the
 * judge's markdown, the judge identity, and the label→provider mapping behind the anonymous "Modelo
 * A/B/…" labels. Only ever built from a recorded (successful) analysis, so unlike the SSE {@link
 * AnalysisEvent} it carries no error state.
 */
public record AnalysisSummary(
    String text, Provider provider, String model, Map<String, Provider> labels) {}
