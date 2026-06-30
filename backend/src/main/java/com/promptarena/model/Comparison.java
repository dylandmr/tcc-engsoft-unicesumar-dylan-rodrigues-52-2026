package com.promptarena.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<ProviderResult> getResults() {
    return results;
  }
}
