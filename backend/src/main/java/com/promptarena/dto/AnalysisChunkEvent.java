package com.promptarena.dto;

/**
 * An incremental text delta streamed from the judge as it generates the comparative analysis
 * (FR-021), emitted as an SSE {@code analysis-chunk} event. Generation only — replaying a recorded
 * analysis emits no chunks. The terminal {@code analysis} event still carries the aggregated text.
 */
public record AnalysisChunkEvent(String delta) {}
