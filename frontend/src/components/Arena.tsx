import { Link } from 'react-router-dom'
import type { ProviderId } from '../types'
import { useArena } from '../hooks/useArena'
import { ProviderLane } from './ui/ProviderLane'
import { Logo } from './ui/Logo'
import { cn } from '../lib/cn'

interface ArenaProps {
  comparisonId: string
  providers: ProviderId[]
  prompt: string
}

/** The live results arena: one race-lane per provider, filling independently. */
export function Arena({ comparisonId, providers, prompt }: ArenaProps) {
  const state = useArena(comparisonId, providers)

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
          <ProviderLane key={id} lane={state.lanes[id]} index={index} />
        ))}
      </div>
    </div>
  )
}
