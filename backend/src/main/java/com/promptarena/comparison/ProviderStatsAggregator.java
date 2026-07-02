package com.promptarena.comparison;

import com.promptarena.dto.ProviderStats;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.repository.ProviderStatsRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Folds a caller's raw provider-result rows into the per-provider aggregate entries of the stats
 * contract (FR-023). Pure logic, no Spring. Telemetry can be absent per row (FR-019), so every
 * average is computed only over the rows that carry the values it needs and is null when none do.
 */
final class ProviderStatsAggregator {

  private ProviderStatsAggregator() {}

  /**
   * One entry per provider with at least one row, ordered by average response time ascending
   * (fastest first). Grouping into an {@link EnumMap} pins {@link Provider} declaration order and
   * the sort is stable, so entries without an average land last — and ties among averages stay — in
   * declaration order (deterministic, per the contract).
   */
  static List<ProviderStats> aggregate(List<ProviderStatsRow> rows) {
    Map<Provider, List<ProviderStatsRow>> byProvider = new EnumMap<>(Provider.class);
    for (ProviderStatsRow row : rows) {
      byProvider.computeIfAbsent(row.provider(), key -> new ArrayList<>()).add(row);
    }
    List<ProviderStats> stats = new ArrayList<>();
    byProvider.forEach((provider, providerRows) -> stats.add(toStats(provider, providerRows)));
    stats.sort(
        Comparator.comparing(
            ProviderStats::avgResponseTimeMs, Comparator.nullsLast(Comparator.naturalOrder())));
    return stats;
  }

  private static ProviderStats toStats(Provider provider, List<ProviderStatsRow> rows) {
    Map<Outcome, Long> outcomes = new EnumMap<>(Outcome.class);
    for (ProviderStatsRow row : rows) {
      outcomes.merge(row.outcome(), 1L, Long::sum);
    }
    return new ProviderStats(
        provider,
        rows.size(),
        outcomes.getOrDefault(Outcome.SUCCESS, 0L),
        outcomes.getOrDefault(Outcome.EMPTY, 0L),
        outcomes.getOrDefault(Outcome.ERROR, 0L),
        outcomes.getOrDefault(Outcome.TIMEOUT, 0L),
        rows.stream().filter(row -> row.responseTimeMs() != null).count(),
        averageMs(rows, ProviderStatsRow::responseTimeMs),
        averageMs(rows, ProviderStatsRow::firstTokenMs),
        averageTokensPerSecond(rows));
  }

  /** Mean of one telemetry field over the rows that recorded it, rounded to whole milliseconds. */
  private static Long averageMs(
      List<ProviderStatsRow> rows, Function<ProviderStatsRow, Long> field) {
    long sum = 0;
    long count = 0;
    for (ProviderStatsRow row : rows) {
      Long value = field.apply(row);
      if (value != null) {
        sum += value;
        count++;
      }
    }
    return count == 0 ? null : Math.round((double) sum / count);
  }

  /**
   * Mean of each qualifying row's output tokens over its first-token→completion window — the same
   * per-run definition the post-race summary uses. A row qualifies only when all three values are
   * present and the window is positive (a zero or negative window carries no meaningful rate).
   */
  private static Double averageTokensPerSecond(List<ProviderStatsRow> rows) {
    double sum = 0;
    long count = 0;
    for (ProviderStatsRow row : rows) {
      if (row.outputTokens() == null
          || row.responseTimeMs() == null
          || row.firstTokenMs() == null) {
        continue;
      }
      long windowMs = row.responseTimeMs() - row.firstTokenMs();
      if (windowMs <= 0) {
        continue;
      }
      sum += row.outputTokens() / (windowMs / 1000.0);
      count++;
    }
    return count == 0 ? null : sum / count;
  }
}
