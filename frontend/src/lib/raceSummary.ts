import type { ProviderId } from '../types'
import type { ArenaState, LaneState, LaneStatus } from '../hooks/arenaReducer'

/** How a row participates in the post-race ranking. */
export type SummaryRowKind = 'ranked' | 'untimed' | 'fault' | 'disabled'

/** One provider's line in the "Resumo da corrida" drawer. */
export interface SummaryRow {
  provider: ProviderId
  kind: SummaryRowKind
  status: LaneStatus
  /** 1-based position among timed responders; null for everything else. */
  rank: number | null
  responseTimeMs: number | null
  /** Gap to the winner's responseTimeMs; null for the winner itself. */
  deltaMs: number | null
  /** responseTimeMs / slowest ranked responseTimeMs, for the latency bar. */
  barFraction: number | null
  firstTokenMs: number | null
  outputTokens: number | null
  /** Decode throughput over the streaming window (first token → done). */
  tokensPerSecond: number | null
  model: string | null
  /** Answer size in characters. */
  chars: number
  errorMessage: string | null
}

export interface RaceSummary {
  rows: SummaryRow[]
  /** The fastest responder by persisted responseTimeMs — the badge owner. */
  winner: ProviderId | null
  /** False when no lane produced a timed response (all failed / stream error). */
  hasData: boolean
}

/** A lane that produced an answer (possibly empty) rather than a fault. */
function responded(lane: LaneState): boolean {
  return lane.status === 'done' || lane.status === 'empty'
}

/**
 * Decode throughput: output tokens over the streaming window (first token →
 * completion). Null when the provider reported no token count, no first-token
 * time, no total time, or a degenerate (≤ 0 ms) window.
 */
function tokensPerSecond(lane: LaneState): number | null {
  if (
    lane.outputTokens === null ||
    lane.firstTokenMs === null ||
    lane.responseTimeMs === null
  )
    return null
  const windowMs = lane.responseTimeMs - lane.firstTokenMs
  if (windowMs <= 0) return null
  return lane.outputTokens / (windowMs / 1000)
}

function baseRow(lane: LaneState): SummaryRow {
  return {
    provider: lane.provider,
    kind: 'untimed',
    status: lane.status,
    rank: null,
    responseTimeMs: lane.responseTimeMs,
    deltaMs: null,
    barFraction: null,
    firstTokenMs: lane.firstTokenMs,
    outputTokens: lane.outputTokens,
    tokensPerSecond: tokensPerSecond(lane),
    model: lane.model,
    chars: lane.text.length,
    errorMessage: lane.errorMessage,
  }
}

/**
 * Derive the post-race summary from the arena state. The winner (and every
 * rank) comes from the persisted responseTimeMs — never from SSE arrival
 * order, which lies on history replay (results are re-emitted in selection
 * order there).
 *
 * Row order: timed responders ranked ascending by responseTimeMs (stable
 * tie-break = selection order), then responders without a recorded time,
 * then faults (error/timeout), then unconfigured providers last.
 */
export function buildRaceSummary(state: ArenaState): RaceSummary {
  const lanes = state.order.map((id) => state.lanes[id])
  const timed = lanes
    .filter((lane) => responded(lane) && lane.responseTimeMs !== null)
    // Array#sort is stable, and `lanes` is in selection order — ties keep it.
    .sort((a, b) => a.responseTimeMs! - b.responseTimeMs!)

  const winnerMs = timed.length > 0 ? timed[0].responseTimeMs! : 0
  const slowestMs =
    timed.length > 0 ? timed[timed.length - 1].responseTimeMs! : 0

  const rows: SummaryRow[] = timed.map((lane, i) => ({
    ...baseRow(lane),
    kind: 'ranked',
    rank: i + 1,
    deltaMs: i === 0 ? null : lane.responseTimeMs! - winnerMs,
    barFraction: slowestMs === 0 ? 1 : lane.responseTimeMs! / slowestMs,
  }))

  for (const lane of lanes) {
    if (responded(lane) && lane.responseTimeMs === null)
      rows.push(baseRow(lane))
  }
  for (const lane of lanes) {
    if (lane.status === 'error' || lane.status === 'timeout')
      rows.push({ ...baseRow(lane), kind: 'fault' })
  }
  for (const lane of lanes) {
    if (lane.status === 'disabled')
      rows.push({ ...baseRow(lane), kind: 'disabled' })
  }

  return {
    rows,
    winner: timed.length > 0 ? timed[0].provider : null,
    hasData: timed.length > 0,
  }
}
