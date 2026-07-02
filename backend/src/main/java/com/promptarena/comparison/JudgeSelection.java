package com.promptarena.comparison;

import com.promptarena.model.Provider;

/**
 * The validated judge choice (provider + model) for one analysis generation (FR-021). Produced by
 * {@link AnalysisService#prepare} — which returns {@code null} instead when a recorded analysis
 * will be replayed (the judge parameters are then ignored per the contract).
 */
record JudgeSelection(Provider provider, String model) {}
