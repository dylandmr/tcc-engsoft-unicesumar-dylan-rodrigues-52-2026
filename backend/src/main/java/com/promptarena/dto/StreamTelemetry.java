package com.promptarena.dto;

/**
 * Telemetry an adapter harvests while streaming a successful call (FR-019): time-to-first-token
 * (measured inside the adapter, on the same clock epoch as the response time, so derived rates like
 * tokens/s are consistent), the provider-reported input/output token usage, and the exact model
 * identifier the provider says answered. Every field is nullable — providers differ in what they
 * report, and a value they never sent is recorded as absent.
 */
public record StreamTelemetry(
    Long firstTokenMs, Long inputTokens, Long outputTokens, String model) {

  /** No telemetry at all — e.g. a stubbed call that never streamed. */
  public static StreamTelemetry none() {
    return new StreamTelemetry(null, null, null, null);
  }
}
