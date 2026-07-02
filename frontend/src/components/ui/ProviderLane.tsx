import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import type { LaneState } from '../../hooks/arenaReducer'
import { providerMeta } from '../../lib/providers'
import { PROVIDER_STYLES } from '../../lib/providerStyles'
import { laneStatusInfo } from '../../lib/laneStatus'
import { countTokens, formatLatency } from '../../lib/format'
import { cn } from '../../lib/cn'
import { Markdown } from './Markdown'

/** How long the "copiado ✓" confirmation lingers before reverting. */
const COPIED_MS = 1500

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
  const isLive = lane.status === 'live'
  const isDisabled = lane.status === 'disabled'
  const isFault = lane.status === 'error' || lane.status === 'timeout'
  const latency = formatLatency(lane.responseTimeMs ?? lane.elapsedMs)
  const latencyTone = isLive || lane.first ? 'text-ignition' : 'text-mist'

  // Auto-follow the stream: while live, keep the lane pinned to the newest
  // tokens; once the lane settles, hand scroll control back to the reader.
  const bodyRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (lane.status === 'live') {
      const el = bodyRef.current!
      el.scrollTop = el.scrollHeight
    }
  }, [lane.status, lane.text])

  const [copied, setCopied] = useState(false)
  const copy = () => {
    void navigator.clipboard.writeText(lane.text)
    setCopied(true)
    setTimeout(() => setCopied(false), COPIED_MS)
  }

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
        'relative flex h-full min-h-0 flex-col overflow-hidden rounded-[var(--radius-panel)] border bg-deck shadow-[inset_0_1px_0_0_rgba(255,255,255,0.04),0_8px_24px_-12px_rgba(0,0,0,0.8)]',
        isDisabled ? 'border-line/60' : 'border-line',
      )}
    >
      <span
        className={cn(
          'absolute inset-x-0 top-0 h-1 origin-top',
          isDisabled ? 'bg-line' : style.bar,
          isLive && style.glow,
          // Settle beat: the bar flares once as the lane leaves 'live'.
          !isDisabled && !isLive && 'animate-[settle_0.5s_ease-out]',
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
          lane.first && 'animate-[first-pop_0.3s_ease-out]',
        )}
      >
        <span
          className={cn(
            'size-1.5 rounded-full bg-current',
            isLive && 'animate-pulse',
          )}
        />
        {status.label}
      </div>

      <div
        ref={bodyRef}
        className={cn(
          'min-h-0 flex-1 overflow-y-auto px-5 py-4',
          isLive && 'streaming',
        )}
      >
        {isDisabled ? (
          <p className="text-sm text-mist">
            Nenhuma chave de API configurada para este provedor.
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
          <p className="text-sm text-mist">Nenhum conteúdo retornado.</p>
        ) : (
          <Markdown>{lane.text}</Markdown>
        )}
      </div>

      {lane.text !== '' && !isDisabled && (
        <motion.footer
          initial={{ opacity: 0, y: 4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.25 }}
          className="flex items-center justify-between border-t border-line px-5 py-3 font-mono text-xs text-mist"
        >
          {/* API truth when reported; otherwise an estimate, never labeled "tokens". */}
          <span>
            {lane.outputTokens != null
              ? `${lane.outputTokens} tokens`
              : `~${countTokens(lane.text)} palavras`}
          </span>
          <button
            type="button"
            onClick={copy}
            className={cn(
              'rounded px-2 py-0.5 transition-colors hover:bg-line/40 hover:text-bright',
              copied && 'text-ignition',
            )}
          >
            {copied ? 'copiado ✓' : 'copiar'}
          </button>
        </motion.footer>
      )}
    </motion.section>
  )
}
