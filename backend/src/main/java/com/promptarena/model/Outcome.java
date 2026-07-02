package com.promptarena.model;

/** The result state of a single provider call (FR-011, FR-012, FR-013). */
public enum Outcome {
  /** Returned non-empty content. */
  SUCCESS,
  /** Returned successfully but with no content. */
  EMPTY,
  /** Call failed (HTTP error, SDK exception, invalid response). */
  ERROR,
  /** Exceeded the per-provider response time limit. */
  TIMEOUT
}
