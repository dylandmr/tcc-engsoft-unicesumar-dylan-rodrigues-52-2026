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
   * The model each selected provider runs, resolved at {@code POST} time (the user's explicit
   * choice or the provider's configured default — FR-020). Comparisons persisted before model
   * selection existed have no entries; dispatch then falls back to the provider's default.
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
}
