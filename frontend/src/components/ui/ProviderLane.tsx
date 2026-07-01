import { motion } from 'framer-motion'
import type { LaneState } from '../../hooks/arenaReducer'
import { providerMeta } from '../../lib/providers'
import { PROVIDER_STYLES } from '../../lib/providerStyles'
import { laneStatusInfo } from '../../lib/laneStatus'
import { countTokens, formatLatency } from '../../lib/format'
import { cn } from '../../lib/cn'
import { Markdown } from './Markdown'

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
  const isDisabled = lane.status === 'disabled'
  const isFault = lane.status === 'error' || lane.status === 'timeout'
  const latency = formatLatency(lane.responseTimeMs ?? lane.elapsedMs)
  const latencyTone =
    lane.status === 'live' || lane.first ? 'text-ignition' : 'text-mist'

  const copy = () => navigator.clipboard.writeText(lane.text)

  return (
    <motion.section
      aria-label={meta.label}
      initial={{ opacity: 0, y: 16 }}
      // Unconfigured providers read as dimmed and slightly smaller — disabled,
      // not failed.
      animate={{
        opacity: isDisabled ? 0.55 : 1,
        scale: isDisabled ? 0.97 : 1,
        y: 0,
      }}
      transition={{ delay: index * 0.06 }}
      className={cn(
        'relative flex h-full min-h-0 flex-col overflow-hidden rounded-[var(--radius-panel)] border bg-deck',
        isDisabled ? 'border-line/60' : 'border-line',
      )}
    >
      <span
        className={cn(
          'absolute inset-x-0 top-0 h-1',
          isDisabled ? 'bg-line' : style.bar,
        )}
      />
      <header className="flex items-baseline justify-between px-5 pt-5">
        <h2
          className={cn(
            'font-display text-xl font-bold',
            isDisabled ? 'text-mist' : 'text-bright',
          )}
        >
          {meta.label}
        </h2>
        {!isDisabled && (
          <span className={cn('font-mono text-sm', latencyTone)}>
            {latency}
          </span>
        )}
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
        {isDisabled ? (
          <p className="text-sm text-mist">
            No API key configured for this provider.
          </p>
        ) : isFault ? (
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
          <Markdown>{lane.text}</Markdown>
        )}
      </div>

      {lane.text !== '' && !isDisabled && (
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
