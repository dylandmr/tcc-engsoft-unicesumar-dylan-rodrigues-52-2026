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

  protected ProviderResult() {
    // for JPA
  }

  public ProviderResult(
      Provider provider,
      Outcome outcome,
      String responseText,
      String errorMessage,
      Long responseTimeMs) {
    this.provider = provider;
    this.outcome = outcome;
    this.responseText = responseText;
    this.errorMessage = errorMessage;
    this.responseTimeMs = responseTimeMs;
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
}
