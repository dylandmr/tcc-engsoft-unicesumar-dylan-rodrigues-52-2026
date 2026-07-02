package com.promptarena.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/** One provider's outcome for a comparison (FR-011, FR-013, FR-014). */
@Entity
@Table(
    name = "provider_results",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_comparison_provider",
            columnNames = {"comparison_id", "provider"}))
public class ProviderResult {

  @Id private String id = UUID.randomUUID().toString();

  @ManyToOne(optional = false)
  @JoinColumn(name = "comparison_id", nullable = false)
  private Comparison comparison;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Provider provider;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Outcome outcome;

  @Column(columnDefinition = "text")
  private String responseText;

  private String errorMessage;

  private Long responseTimeMs;

  /** Time-to-first-token, same clock epoch as {@code responseTimeMs} (FR-019). Nullable. */
  @Column(name = "first_token_ms")
  private Long firstTokenMs;

  /** Provider-reported prompt token count (FR-019). Nullable. */
  @Column(name = "input_tokens")
  private Long inputTokens;

  /** Provider-reported completion token count (FR-019). Nullable. */
  @Column(name = "output_tokens")
  private Long outputTokens;

  /** Exact model identifier the provider reports as having answered (FR-019). Nullable. */
  @Column(name = "model")
  private String model;

  protected ProviderResult() {
    // for JPA
  }

  /** A result without telemetry (failure shapes, or telemetry the provider never reported). */
  public ProviderResult(
      Provider provider,
      Outcome outcome,
      String responseText,
      String errorMessage,
      Long responseTimeMs) {
    this(provider, outcome, responseText, errorMessage, responseTimeMs, null, null, null, null);
  }

  public ProviderResult(
      Provider provider,
      Outcome outcome,
      String responseText,
      String errorMessage,
      Long responseTimeMs,
      Long firstTokenMs,
      Long inputTokens,
      Long outputTokens,
      String model) {
    this.provider = provider;
    this.outcome = outcome;
    this.responseText = responseText;
    this.errorMessage = errorMessage;
    this.responseTimeMs = responseTimeMs;
    this.firstTokenMs = firstTokenMs;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.model = model;
  }

  void setComparison(Comparison comparison) {
    this.comparison = comparison;
  }

  public String getId() {
    return id;
  }

  public Comparison getComparison() {
    return comparison;
  }

  public Provider getProvider() {
    return provider;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public String getResponseText() {
    return responseText;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Long getResponseTimeMs() {
    return responseTimeMs;
  }

  public Long getFirstTokenMs() {
    return firstTokenMs;
  }

  public Long getInputTokens() {
    return inputTokens;
  }

  public Long getOutputTokens() {
    return outputTokens;
  }

  public String getModel() {
    return model;
  }
}
