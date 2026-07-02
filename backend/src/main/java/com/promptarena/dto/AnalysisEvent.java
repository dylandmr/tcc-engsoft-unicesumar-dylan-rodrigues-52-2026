package com.promptarena.dto;

import com.promptarena.model.Provider;
import java.util.Map;

/**
 * Payload of the terminal SSE {@code analysis} event (FR-021). On success {@code text} carries the
 * judge's markdown and {@code errorMessage} is null; on judge failure {@code text} is null, {@code
 * errorMessage} is set, and nothing was persisted (the user may retry with any judge). {@code
 * provider}/{@code model} identify the judge (recorded, or attempted on failure) and {@code labels}
 * maps each anonymous label ("A", "B", …) to the provider whose answer it hid.
 */
public record AnalysisEvent(
    String text,
    String errorMessage,
    Provider provider,
    String model,
    Map<String, Provider> labels) {}
