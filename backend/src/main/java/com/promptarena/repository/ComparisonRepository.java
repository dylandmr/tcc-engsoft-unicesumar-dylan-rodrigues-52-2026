package com.promptarena.repository;

import com.promptarena.model.Comparison;
import com.promptarena.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComparisonRepository extends JpaRepository<Comparison, String> {

  List<Comparison> findByUserOrderByCreatedAtDesc(User user);

  Optional<Comparison> findByIdAndUser(String id, User user);

  /**
   * Delete every comparison owned by {@code user} (FR-022). A derived delete loads and removes the
   * entities one by one, so the {@code results} cascade and the element collections ({@code
   * comparison_providers}, {@code comparison_models}, {@code comparison_analysis_order}) are all
   * cleaned up — a bulk JPQL {@code DELETE} would bypass cascading and orphan those rows. Must run
   * inside a caller-provided transaction.
   */
  void deleteByUser(User user);

  /**
   * Every provider-result row recorded for {@code user}'s comparisons, projected down to the
   * telemetry the FR-023 aggregates need. Joining through the owning comparison scopes the rows
   * strictly to the caller (FR-016); the aggregation itself happens in Java at read time — nothing
   * is persisted for it.
   */
  @Query(
      """
      select new com.promptarena.repository.ProviderStatsRow(
          r.provider, r.outcome, r.responseTimeMs, r.firstTokenMs, r.outputTokens)
      from ProviderResult r
      where r.comparison.user = :user
      """)
  List<ProviderStatsRow> findStatsRowsByUser(@Param("user") User user);
}
