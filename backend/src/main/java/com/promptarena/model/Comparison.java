package com.promptarena.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A single submitted prompt and the set of providers it targeted (FR-014). */
@Entity
@Table(name = "comparisons")
public class Comparison {

  @Id private String id = UUID.randomUUID().toString();

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 10_000)
  private String prompt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  /**
   * The providers selected for this comparison, persisted on {@code POST} while the comparison is
   * still {@link Status#PENDING} (before any {@link ProviderResult} exists). This is the source of
   * truth for "which providers" the lazy fan-out should dispatch to when the stream is opened.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "comparison_providers", joinColumns = @JoinColumn(name = "comparison_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private List<Provider> providers = new ArrayList<>();

  /**
   * The model each selected provider runs, chosen explicitly by the user at {@code POST} time
   * (FR-020 — no defaults exist). Comparisons persisted before model selection existed have no
   * entries; dispatch never calls such a provider and records its own ERROR result instead.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "comparison_models", joinColumns = @JoinColumn(name = "comparison_id"))
  @MapKeyEnumerated(EnumType.STRING)
  @MapKeyColumn(name = "provider")
  @Column(name = "model", nullable = false)
  private Map<Provider, String> models = new EnumMap<>(Provider.class);

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @OneToMany(mappedBy = "comparison", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ProviderResult> results = new ArrayList<>();

  /**
   * The recorded comparative analysis (FR-021), generated on demand by a user-picked judge after
   * completion. Null until {@link #recordAnalysis} runs; once recorded it is immutable and replayed
   * instead of regenerated. A judge failure records nothing.
   */
  @Column(name = "analysis_text", columnDefinition = "text")
  private String analysisText;

  /** The judge provider that produced the recorded analysis (FR-021). Null until recorded. */
  @Enumerated(EnumType.STRING)
  @Column(name = "analysis_provider")
  private Provider analysisProvider;

  /** The judge model that produced the recorded analysis (FR-021). Null until recorded. */
  @Column(name = "analysis_model")
  private String analysisModel;

  /**
   * The randomized provider order the judge saw, backing the anonymous "Modelo A/B/…" labels
   * (FR-021): index 0 is label "A", index 1 is "B", and so on. Empty until an analysis is recorded.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "comparison_analysis_order",
      joinColumns = @JoinColumn(name = "comparison_id"))
  @OrderColumn(name = "position")
  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private List<Provider> analysisOrder = new ArrayList<>();

  protected Comparison() {
    // for JPA
  }

  public Comparison(User user, String prompt) {
    this.user = user;
    this.prompt = prompt;
  }

  public Comparison(User user, String prompt, List<Provider> providers) {
    this.user = user;
    this.prompt = prompt;
    this.providers = new ArrayList<>(providers);
  }

  public Comparison(
      User user, String prompt, List<Provider> providers, Map<Provider, String> models) {
    this(user, prompt, providers);
    this.models.putAll(models);
  }

  public void addResult(ProviderResult result) {
    result.setComparison(this);
    this.results.add(result);
  }

  public void markComplete() {
    this.status = Status.COMPLETE;
  }

  /**
   * Record the generated comparative analysis (FR-021): the judge's markdown, the judge identity,
   * and the randomized provider order behind the anonymous labels (index 0 = "A").
   */
  public void recordAnalysis(String text, Provider provider, String model, List<Provider> order) {
    this.analysisText = text;
    this.analysisProvider = provider;
    this.analysisModel = model;
    this.analysisOrder.clear();
    this.analysisOrder.addAll(order);
  }

  /** Whether a comparative analysis has been recorded (it is then replayed, never regenerated). */
  public boolean hasAnalysis() {
    return analysisText != null;
  }

  public String getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getPrompt() {
    return prompt;
  }

  public Status getStatus() {
    return status;
  }

  public List<Provider> getProviders() {
    return providers;
  }

  public Map<Provider, String> getModels() {
    return models;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<ProviderResult> getResults() {
    return results;
  }

  public String getAnalysisText() {
    return analysisText;
  }

  public Provider getAnalysisProvider() {
    return analysisProvider;
  }

  public String getAnalysisModel() {
    return analysisModel;
  }

  public List<Provider> getAnalysisOrder() {
    return analysisOrder;
  }
}
