package com.promptarena.model;

/** Lifecycle of a {@link Comparison}. */
public enum Status {
  /** Created and validated; the fan-out has not run yet. */
  PENDING,
  /** Fan-out finished and provider results are persisted. */
  COMPLETE
}
