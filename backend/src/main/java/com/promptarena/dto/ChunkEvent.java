package com.promptarena.dto;

import com.promptarena.model.Provider;

/**
 * An incremental text delta streamed from a provider as it generates its answer, emitted as an SSE
 * {@code chunk} event so the SPA can render the response progressively (like a live chatbot). The
 * final {@code result} event still carries the aggregated text, outcome and latency.
 */
public record ChunkEvent(Provider provider, String delta) {}
