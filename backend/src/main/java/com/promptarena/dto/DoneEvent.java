package com.promptarena.dto;

/** Payload of the terminal SSE {@code done} event: the comparison id and provider count reported. */
public record DoneEvent(String comparisonId, int completed) {}
