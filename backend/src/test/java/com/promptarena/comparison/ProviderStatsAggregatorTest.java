package com.promptarena.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import com.promptarena.dto.ProviderStats;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.repository.ProviderStatsRow;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProviderStatsAggregator} (FR-023) — the pure fold from raw provider-result
 * rows to the contract's per-provider aggregate entries. Telemetry is nullable per row (FR-019), so
 * every average must state its own basis and go null when no row qualifies. Endpoint-level behavior
 * (auth, scoping, routing) is covered by {@code StatsEndpointTest}.
 */
class ProviderStatsAggregatorTest {

  private static ProviderStatsRow row(
      Provider provider,
      Outcome outcome,
      Long responseTimeMs,
      Long firstTokenMs,
      Long outputTokens) {
    return new ProviderStatsRow(provider, outcome, responseTimeMs, firstTokenMs, outputTokens);
  }

  @Test
  void noRowsYieldNoEntries() {
    assertThat(ProviderStatsAggregator.aggregate(List.of())).isEmpty();
  }

  @Test
  void mixedOutcomesAreCountedAndRunsIsTheirSum() {
    List<ProviderStats> stats =
        ProviderStatsAggregator.aggregate(
            List.of(
                row(Provider.GEMINI, Outcome.SUCCESS, 1000L, 200L, 50L),
                row(Provider.GEMINI, Outcome.SUCCESS, 2000L, 400L, 90L),
                row(Provider.GEMINI, Outcome.EMPTY, 500L, 500L, 0L),
                row(Provider.GEMINI, Outcome.ERROR, null, null, null),
                row(Provider.GEMINI, Outcome.TIMEOUT, null, null, null)));

    assertThat(stats).hasSize(1);
    ProviderStats gemini = stats.get(0);
    assertThat(gemini.provider()).isEqualTo(Provider.GEMINI);
    assertThat(gemini.successes()).isEqualTo(2);
    assertThat(gemini.empties()).isEqualTo(1);
    assertThat(gemini.errors()).isEqualTo(1);
    assertThat(gemini.timeouts()).isEqualTo(1);
    assertThat(gemini.runs())
        .isEqualTo(gemini.successes() + gemini.empties() + gemini.errors() + gemini.timeouts());
  }

  @Test
  void telemetryRunsCountsOnlyRowsWithAResponseTime() {
    List<ProviderStats> stats =
        ProviderStatsAggregator.aggregate(
            List.of(
                row(Provider.CLAUDE, Outcome.SUCCESS, 1000L, null, null),
                row(Provider.CLAUDE, Outcome.SUCCESS, null, 300L, 40L),
                row(Provider.CLAUDE, Outcome.TIMEOUT, null, null, null)));

    assertThat(stats.get(0).telemetryRuns()).isEqualTo(1);
    assertThat(stats.get(0).runs()).isEqualTo(3);
  }

  @Test
  void eachAverageIsComputedOnlyOverTheRowsThatCarryItsValues() {
    // Row 1 carries only a response time; row 2 only a first-token time; row 3 both times but no
    // output tokens; row 4 tokens and a response time but no first-token time — so no row
    // qualifies for tokens/s and each ms average has its own basis.
    List<ProviderStats> stats =
        ProviderStatsAggregator.aggregate(
            List.of(
                row(Provider.GROK, Outcome.SUCCESS, 1000L, null, null),
                row(Provider.GROK, Outcome.SUCCESS, null, 250L, null),
                row(Provider.GROK, Outcome.SUCCESS, 2000L, 750L, null),
                row(Provider.GROK, Outcome.SUCCESS, 3000L, null, 40L)));

    ProviderStats grok = stats.get(0);
    assertThat(grok.avgResponseTimeMs()).isEqualTo(2000L);
    assertThat(grok.avgFirstTokenMs()).isEqualTo(500L);
    assertThat(grok.avgTokensPerSecond()).isNull();
  }

  @Test
  void msAveragesRoundToTheNearestMillisecond() {
    List<ProviderStats> stats =
        ProviderStatsAggregator.aggregate(
            List.of(
                row(Provider.GEMINI, Outcome.SUCCESS, 1000L, 100L, null),
                row(Provider.GEMINI, Outcome.SUCCESS, 1001L, 102L, null)));

    // 1000.5 rounds up; 101.0 is exact.
    assertThat(stats.get(0).avgResponseTimeMs()).isEqualTo(1001L);
    assertThat(stats.get(0).avgFirstTokenMs()).isEqualTo(101L);
  }

  @Test
  void tokensPerSecondAveragesThePerRowRatesOfQualifyingRows() {
    // 60 tokens over 1.5 s = 40 t/s; 25 tokens over 0.5 s = 50 t/s → mean 45.0.
    List<ProviderStats> stats =
        ProviderStatsAggregator.aggregate(
            List.of(
                row(Provider.GEMINI, Outcome.SUCCESS, 2000L, 500L, 60L),
                row(Provider.GEMINI, Outcome.SUCCESS, 1000L, 500L, 25L)));

    assertThat(stats.get(0).avgTokensPerSecond()).isEqualTo(45.0);
  }

  @Test
  void zeroAndNegativeStreamingWindowsAreExcludedFromTokensPerSecond() {
    // Zero window (first token at completion) and a negative window (inconsistent clocks) carry no
    // meaningful rate; the one positive-window row is the whole basis.
    List<ProviderStats> stats =
        ProviderStatsAggregator.aggregate(
            List.of(
                row(Provider.CLAUDE, Outcome.SUCCESS, 1000L, 1000L, 80L),
                row(Provider.CLAUDE, Outcome.SUCCESS, 1000L, 1200L, 80L),
                row(Provider.CLAUDE, Outcome.SUCCESS, 2000L, 1000L, 30L)));

    assertThat(stats.get(0).avgTokensPerSecond()).isEqualTo(30.0);
  }

  @Test
  void everyAverageIsNullWhenNoRowQualifies() {
    List<ProviderStats> stats =
        ProviderStatsAggregator.aggregate(
            List.of(
                row(Provider.DEEPSEEK, Outcome.ERROR, null, null, null),
                row(Provider.DEEPSEEK, Outcome.TIMEOUT, null, null, null)));

    ProviderStats deepseek = stats.get(0);
    assertThat(deepseek.telemetryRuns()).isZero();
    assertThat(deepseek.avgResponseTimeMs()).isNull();
    assertThat(deepseek.avgFirstTokenMs()).isNull();
    assertThat(deepseek.avgTokensPerSecond()).isNull();
  }

  @Test
  void entriesAreOrderedFastestFirstWithNullAveragesLastInDeclarationOrder() {
    // Averages: CLAUDE 1000 < GEMINI 2000; GROK and CHATGPT have none and must trail in Provider
    // declaration order (CHATGPT before GROK) regardless of row order.
    List<ProviderStats> stats =
        ProviderStatsAggregator.aggregate(
            List.of(
                row(Provider.GROK, Outcome.TIMEOUT, null, null, null),
                row(Provider.GEMINI, Outcome.SUCCESS, 2000L, null, null),
                row(Provider.CHATGPT, Outcome.ERROR, null, null, null),
                row(Provider.CLAUDE, Outcome.SUCCESS, 1000L, null, null)));

    assertThat(stats)
        .extracting(ProviderStats::provider)
        .containsExactly(Provider.CLAUDE, Provider.GEMINI, Provider.CHATGPT, Provider.GROK);
  }

  @Test
  void tiedAveragesFallBackToDeclarationOrder() {
    List<ProviderStats> stats =
        ProviderStatsAggregator.aggregate(
            List.of(
                row(Provider.CLAUDE, Outcome.SUCCESS, 1500L, null, null),
                row(Provider.GEMINI, Outcome.SUCCESS, 1500L, null, null)));

    assertThat(stats)
        .extracting(ProviderStats::provider)
        .containsExactly(Provider.GEMINI, Provider.CLAUDE);
  }
}
