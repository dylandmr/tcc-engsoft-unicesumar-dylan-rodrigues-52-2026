import { Link } from 'react-router-dom'
import { AnimatePresence } from 'framer-motion'
import type { ModelSelection, ProviderId } from '../types'
import { useArena } from '../hooks/useArena'
import { buildRaceSummary } from '../lib/raceSummary'
import { ProviderLane } from './ui/ProviderLane'
import { RaceSummary } from './ui/RaceSummary'
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
  const state = useArena(comparisonId, providers)
  // The winner badge and the post-race drawer are both derived from persisted
  // responseTimeMs — SSE arrival order lies on history replay (results are
  // re-emitted in selection order there).
  const summary = buildRaceSummary(state)

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

      <AnimatePresence>
        {state.done && <RaceSummary summary={summary} />}
      </AnimatePresence>
    </div>
  )
}
