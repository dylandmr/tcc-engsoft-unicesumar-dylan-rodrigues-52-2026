import { Link } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import type { ModelSelection, ProviderId } from '../types'
import { useArena } from '../hooks/useArena'
import { buildRaceSummary } from '../lib/raceSummary'
import { ProviderLane } from './ui/ProviderLane'
import { RaceSummary } from './ui/RaceSummary'
import { KeyDifferences } from './ui/KeyDifferences'
import { Logo } from './ui/Logo'
import { cn } from '../lib/cn'

interface ArenaProps {
  comparisonId: string
  providers: ProviderId[]
  prompt: string
  /** Requested model per provider (nav state, FR-020); absent on history replay. */
  models?: ModelSelection
}

/** The live results arena: one race-lane per provider, filling independently. */
export function Arena({ comparisonId, providers, prompt, models }: ArenaProps) {
  const { state, startAnalysis } = useArena(comparisonId, providers)
  // The winner badge and the post-race footer are both derived from persisted
  // responseTimeMs — SSE arrival order lies on history replay (results are
  // re-emitted in selection order there).
  const summary = buildRaceSummary(state)
  // The judge needs ≥2 successful answers to compare (FR-021) — 'done' is
  // exactly the SUCCESS outcome ('empty' lanes have nothing to compare).
  const showAnalysis =
    summary.rows.filter((row) => row.status === 'done').length >= 2

  return (
    <div className="flex h-screen flex-col">
      <header className="flex items-center justify-between gap-4 px-6 py-5">
        <Link to="/" aria-label="Início">
          <Logo />
        </Link>
        <p className="min-w-0 max-w-2xl flex-1 truncate text-center font-body text-lg text-bright md:text-xl">
          “{prompt}”
        </p>
        <span className="flex items-center gap-2 rounded-full border border-line px-3 py-1 font-mono text-xs text-mist">
          <span
            className={cn(
              'size-1.5 rounded-full',
              state.done ? 'bg-mist' : 'animate-pulse bg-ignition',
            )}
          />
          {state.done ? 'concluído' : 'ao vivo'} · {state.order.length} modelos
        </span>
      </header>

      <div
        className="grid min-h-0 flex-1 gap-4 px-6 pb-6"
        style={{
          gridTemplateColumns: `repeat(${state.order.length}, minmax(0, 1fr))`,
        }}
      >
        {state.order.map((id, index) => (
          <ProviderLane
            key={id}
            lane={{ ...state.lanes[id], first: id === summary.winner }}
            index={index}
            requestedModel={models?.[id]}
          />
        ))}
      </div>

      {/*
        Post-race footer: two sibling panels — telemetry left (~60% at lg+),
        judge analysis right (~40%) — stacked below lg, summary first. With
        fewer than two successes the summary takes the full width alone.
      */}
      <AnimatePresence>
        {state.done && (
          <motion.footer
            initial={{ height: 0, opacity: 0, y: 24 }}
            animate={{ height: 'auto', opacity: 1, y: 0 }}
            exit={{ height: 0, opacity: 0, y: 24 }}
            transition={{ type: 'spring', stiffness: 240, damping: 32 }}
            className="shrink-0 overflow-hidden"
          >
            <div
              className={cn(
                'mx-6 mb-6 grid items-start gap-4',
                showAnalysis && 'lg:grid-cols-[3fr_2fr]',
              )}
            >
              <RaceSummary summary={summary} />
              {showAnalysis && (
                <KeyDifferences
                  analysis={state.analysis}
                  onAnalyze={startAnalysis}
                  raceProviders={state.order}
                />
              )}
            </div>
          </motion.footer>
        )}
      </AnimatePresence>
    </div>
  )
}
