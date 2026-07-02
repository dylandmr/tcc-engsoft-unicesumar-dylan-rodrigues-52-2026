import { useState } from 'react'
import { motion } from 'framer-motion'
import type { LaneState } from '../../hooks/arenaReducer'
import type {
  RaceSummary as RaceSummaryData,
  SummaryRow,
} from '../../lib/raceSummary'
import { providerMeta } from '../../lib/providers'
import { PROVIDER_STYLES } from '../../lib/providerStyles'
import { laneStatusInfo } from '../../lib/laneStatus'
import {
  formatDelta,
  formatLatency,
  formatTokensPerSecond,
} from '../../lib/format'
import { cn } from '../../lib/cn'

/** Shared column template: rank · provider · bar · latency · Δ · 1º token · tokens · tok/s. */
const GRID =
  'grid grid-cols-[2.25rem_6.5rem_minmax(3rem,1fr)_4.5rem_4.5rem_4.5rem_4rem_5.5rem] items-center gap-x-3'

/** Same beat as the lane entrances (`delay: index * 0.06`), a touch slower. */
const stagger = (index: number) => ({
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  transition: { delay: index * 0.08 },
})

/** A ranked (or responded-but-untimed) provider's telemetry line. */
function MetricRow({ row, index }: { row: SummaryRow; index: number }) {
  const meta = providerMeta(row.provider)
  const style = PROVIDER_STYLES[row.provider]
  return (
    <motion.div
      {...stagger(index)}
      className="border-t border-line/60 py-2 font-mono text-xs text-bright first:border-t-0"
    >
      <div className={GRID}>
        <span
          className={cn(
            'text-sm',
            row.rank === 1 ? 'text-ignition' : 'text-mist',
          )}
        >
          {row.rank !== null ? `${row.rank}º` : '—'}
        </span>
        <span className="truncate font-display text-sm font-bold">
          {meta.label}
        </span>
        <span className="h-1.5 overflow-hidden rounded-full bg-line/50">
          {row.barFraction !== null && (
            <motion.span
              className={cn('block h-full origin-left rounded-full', style.bar)}
              initial={{ scaleX: 0 }}
              animate={{ scaleX: row.barFraction }}
              transition={{ duration: 0.6, delay: index * 0.08 }}
            />
          )}
        </span>
        <span>
          {row.responseTimeMs !== null
            ? formatLatency(row.responseTimeMs)
            : '—'}
        </span>
        <span className="text-mist">
          {row.deltaMs !== null ? formatDelta(row.deltaMs) : '—'}
        </span>
        <span className="text-mist">
          {row.firstTokenMs !== null ? formatLatency(row.firstTokenMs) : '—'}
        </span>
        <span className="text-mist">{row.outputTokens ?? '—'}</span>
        <span className="text-mist">
          {row.tokensPerSecond !== null
            ? formatTokensPerSecond(row.tokensPerSecond)
            : '—'}
        </span>
      </div>
      <p className="pl-[3rem] pt-1 text-[11px] text-mist">
        {row.model ?? '—'} · {row.chars} caracteres
      </p>
    </motion.div>
  )
}

/** A fault (error/timeout) or unconfigured provider's line — no metrics. */
function StatusRow({ row, index }: { row: SummaryRow; index: number }) {
  const meta = providerMeta(row.provider)
  // laneStatusInfo only reads `status`/`first`, so a summary row can borrow
  // the lanes' labels and tones without duplicating them here.
  const info = laneStatusInfo({ status: row.status, first: false } as LaneState)
  const disabled = row.kind === 'disabled'
  return (
    <motion.div
      {...stagger(index)}
      className={cn(
        'grid grid-cols-[2.25rem_6.5rem_1fr] items-center gap-x-3 border-t border-line/60 py-2 font-mono text-xs first:border-t-0',
        disabled && 'opacity-55',
      )}
    >
      <span className="text-sm text-mist">—</span>
      <span
        className={cn(
          'truncate font-display text-sm font-bold',
          disabled ? 'text-mist' : 'text-bright',
        )}
      >
        {meta.label}
      </span>
      <span className={cn('truncate', info.toneClass)}>
        {info.label}
        {row.kind === 'fault' &&
          row.errorMessage !== null &&
          ` · ${row.errorMessage}`}
      </span>
    </motion.div>
  )
}

const NO_DATA = 'sem dados desta execução'

/**
 * "Resumo da corrida" — the post-race telemetry drawer docked under the lanes
 * (FR-008/FR-019). Slides up once every provider has reported; the lanes'
 * flex-1 grid makes room as it grows.
 */
export function RaceSummary({ summary }: { summary: RaceSummaryData }) {
  const [expanded, setExpanded] = useState(true)
  const winner = summary.rows.find((row) => row.rank === 1)

  return (
    <motion.section
      aria-label="Resumo da corrida"
      initial={{ height: 0, opacity: 0, y: 24 }}
      animate={{ height: 'auto', opacity: 1, y: 0 }}
      exit={{ height: 0, opacity: 0, y: 24 }}
      transition={{ type: 'spring', stiffness: 240, damping: 32 }}
      className="mx-6 mb-6 shrink-0 overflow-hidden rounded-[var(--radius-panel)] border border-line bg-deck shadow-[inset_0_1px_0_0_rgba(255,255,255,0.04),0_8px_24px_-12px_rgba(0,0,0,0.8)]"
    >
      <header className="flex items-baseline gap-4 px-5 pb-2 pt-4">
        <h2 className="font-mono text-xs tracking-[0.18em] text-ignition">
          RESUMO DA CORRIDA
        </h2>
        <span className="hidden font-mono text-xs text-mist sm:inline">
          telemetria desta execução
        </span>
        {!expanded && (
          <span className="truncate font-mono text-xs text-bright">
            {winner !== undefined
              ? `1º ${providerMeta(winner.provider).label} · ${formatLatency(
                  winner.responseTimeMs!,
                )}`
              : NO_DATA}
          </span>
        )}
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => setExpanded((open) => !open)}
          className="ml-auto rounded px-2 py-0.5 font-mono text-xs text-mist transition-colors hover:bg-line/40 hover:text-bright"
        >
          {expanded ? 'recolher' : 'expandir'}
        </button>
      </header>

      {expanded && (
        <div className="max-h-[38vh] overflow-y-auto px-5 pb-4">
          {summary.hasData ? (
            <>
              <div
                className={cn(
                  GRID,
                  'pb-1 font-mono text-[10px] uppercase tracking-[0.18em] text-mist',
                )}
              >
                <span />
                <span />
                <span />
                <span>latência</span>
                <span>Δ líder</span>
                <span>1º token</span>
                <span>tokens</span>
                <span>tok/s</span>
              </div>
              {summary.rows.map((row, index) =>
                row.kind === 'ranked' || row.kind === 'untimed' ? (
                  <MetricRow key={row.provider} row={row} index={index} />
                ) : (
                  <StatusRow key={row.provider} row={row} index={index} />
                ),
              )}
            </>
          ) : (
            <p className="py-1 font-mono text-xs text-mist">{NO_DATA}</p>
          )}
        </div>
      )}
    </motion.section>
  )
}
