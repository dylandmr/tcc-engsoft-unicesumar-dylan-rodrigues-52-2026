import { motion } from 'framer-motion'
import type { LaneState } from '../../hooks/arenaReducer'
import { providerMeta } from '../../lib/providers'
import { PROVIDER_STYLES } from '../../lib/providerStyles'
import { laneStatusInfo } from '../../lib/laneStatus'
import { countTokens, formatLatency } from '../../lib/format'
import { cn } from '../../lib/cn'

/** One provider's live race-lane in the results arena. */
export function ProviderLane({
  lane,
  index = 0,
}: {
  lane: LaneState
  index?: number
}) {
  const meta = providerMeta(lane.provider)
  const style = PROVIDER_STYLES[lane.provider]
  const status = laneStatusInfo(lane)
  const isFault = lane.status === 'error' || lane.status === 'timeout'
  const latency = formatLatency(lane.responseTimeMs ?? lane.elapsedMs)
  const latencyTone =
    lane.status === 'live' || lane.first ? 'text-ignition' : 'text-mist'

  const copy = () => navigator.clipboard.writeText(lane.text)

  return (
    <motion.section
      aria-label={meta.label}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.06 }}
      className="relative flex h-full min-h-0 flex-col overflow-hidden rounded-[var(--radius-panel)] border border-line bg-deck"
    >
      <span className={cn('absolute inset-x-0 top-0 h-1', style.bar)} />
      <header className="flex items-baseline justify-between px-5 pt-5">
        <h2 className="font-display text-xl font-bold text-bright">
          {meta.label}
        </h2>
        <span className={cn('font-mono text-sm', latencyTone)}>{latency}</span>
      </header>

      <div
        className={cn(
          'flex items-center gap-2 px-5 pt-2 font-mono text-xs',
          status.toneClass,
        )}
      >
        <span className="size-1.5 rounded-full bg-current" />
        {status.label}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
        {isFault ? (
          <p
            className={cn(
              'text-sm',
              lane.status === 'error' ? 'text-error' : 'text-timeout',
            )}
          >
            ⚠ {lane.errorMessage}
          </p>
        ) : lane.status === 'empty' ? (
          <p className="text-sm text-mist">No content returned.</p>
        ) : (
          <p className="whitespace-pre-wrap text-sm leading-relaxed text-bright">
            {lane.text}
          </p>
        )}
      </div>

      {lane.text !== '' && (
        <footer className="flex items-center justify-between border-t border-line px-5 py-3 font-mono text-xs text-mist">
          <span>{countTokens(lane.text)} tokens</span>
          <button type="button" onClick={copy} className="hover:text-bright">
            copy
          </button>
        </footer>
      )}
    </motion.section>
  )
}
