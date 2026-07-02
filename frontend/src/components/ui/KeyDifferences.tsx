import { useCallback, useEffect, useState } from 'react'
import type {
  ProviderCatalogEntry,
  ProviderId,
  RecordedAnalysis,
} from '../../types'
import type { AnalysisState } from '../../hooks/arenaReducer'
import { getProviders } from '../../api/client'
import { providerMeta } from '../../lib/providers'
import { PROVIDER_STYLES } from '../../lib/providerStyles'
import { cn } from '../../lib/cn'
import { Markdown } from './Markdown'
import { ModelSelect } from './ModelSelect'

/** Analysis request: the user-picked judge (FR-020: no default judge). */
export type AnalyzeHandler = (
  judgeProvider: ProviderId,
  judgeModel: string,
) => void

/**
 * Compact judge picker: provider + model straight from the live catalog
 * (FR-020 semantics — the user picks both, nothing is preselected). Mounted
 * only while an analysis can be requested, so the catalog fetch is lazy and
 * always fresh on retry.
 */
function JudgePicker({
  onAnalyze,
  raceProviders,
}: {
  onAnalyze: AnalyzeHandler
  raceProviders: ProviderId[]
}) {
  const [catalog, setCatalog] = useState<ProviderCatalogEntry[]>([])
  const [catalogState, setCatalogState] = useState<
    'loading' | 'ready' | 'error'
  >('loading')
  const [judgeProvider, setJudgeProvider] = useState<ProviderId | ''>('')
  const [judgeModel, setJudgeModel] = useState('')

  const fetchCatalog = useCallback(() => {
    setCatalogState('loading')
    getProviders().then(
      (entries) => {
        setCatalog(entries)
        setCatalogState('ready')
      },
      () => setCatalogState('error'),
    )
  }, [])
  useEffect(fetchCatalog, [fetchCatalog])

  if (catalogState === 'loading')
    return <p className="font-mono text-xs text-mist">carregando modelos…</p>
  if (catalogState === 'error')
    return (
      <p role="alert" className="font-mono text-xs text-error">
        Não foi possível carregar os modelos dos provedores.{' '}
        <button
          type="button"
          onClick={fetchCatalog}
          className="text-bright underline-offset-4 hover:underline"
        >
          tentar novamente
        </button>
      </p>
    )

  // Only a non-competitor whose live catalog offers models can judge —
  // FR-020 (explicit model pick) plus FR-021 (the backend rejects a
  // competitor with `judge_in_race`), so competitors never appear as options.
  const judgeable = catalog.filter(
    (entry) =>
      entry.models.length > 0 && !raceProviders.includes(entry.provider),
  )
  if (judgeable.length === 0)
    return (
      <p className="font-mono text-xs text-mist">
        nenhuma juíza disponível fora da corrida — configure outro provedor.
      </p>
    )
  const chosen = judgeable.find((entry) => entry.provider === judgeProvider)

  return (
    <div className="flex flex-wrap items-center gap-2">
      <select
        aria-label="Juíza"
        value={judgeProvider}
        onChange={(event) => {
          setJudgeProvider(event.target.value as ProviderId)
          // A new judge starts with no model — the pick is always explicit.
          setJudgeModel('')
        }}
        className="cursor-pointer rounded-lg border border-line bg-void/60 px-2.5 py-1.5 font-mono text-xs text-bright transition-colors duration-150 hover:border-mist/60 focus:outline-none focus-visible:border-ignition"
      >
        <option value="" disabled>
          selecionar juíza…
        </option>
        {judgeable.map((entry) => (
          <option key={entry.provider} value={entry.provider}>
            {providerMeta(entry.provider).label}
          </option>
        ))}
      </select>
      {chosen !== undefined && (
        <div className="w-56">
          <ModelSelect
            providerId={chosen.provider}
            label="Modelo da juíza"
            value={judgeModel}
            options={chosen.models}
            onChange={setJudgeModel}
          />
        </div>
      )}
      <button
        type="button"
        disabled={judgeProvider === '' || judgeModel === ''}
        onClick={() => onAnalyze(judgeProvider as ProviderId, judgeModel)}
        className="rounded-lg border border-ignition/50 px-3 py-1.5 font-mono text-xs text-ignition transition-colors duration-150 hover:bg-ignition/10 disabled:cursor-not-allowed disabled:opacity-40"
      >
        analisar diferenças
      </button>
    </div>
  )
}

/** The recorded analysis: markdown + label legend + judge attribution. */
function AnalysisReport({ analysis }: { analysis: RecordedAnalysis }) {
  const judge = providerMeta(analysis.provider)
  return (
    <div>
      <Markdown>{analysis.text}</Markdown>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {Object.entries(analysis.labels)
          .sort(([a], [b]) => a.localeCompare(b))
          .map(([label, provider]) => {
            const style = PROVIDER_STYLES[provider]
            return (
              <span
                key={label}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-mono text-[11px] text-bright',
                  style.pill,
                )}
              >
                <span className={cn('size-1.5 rounded-full', style.dot)} />
                Modelo {label} → {providerMeta(provider).label}
              </span>
            )
          })}
      </div>
      <p className="mt-2 font-mono text-[11px] text-mist">
        juíza: {judge.label} · {analysis.model}
      </p>
    </div>
  )
}

/**
 * "DIFERENÇAS-CHAVE" — the on-demand LLM-as-judge analysis panel (FR-021),
 * the right half of the footer row next to "RESUMO DA CORRIDA". Renders the
 * judge picker (competitors barred from judging) until an analysis exists,
 * live-streams the judge's markdown, then shows the recorded analysis with
 * its anonymization legend. Errors are retryable with any eligible judge.
 * Collapses independently of its sibling; long analyses scroll internally.
 */
export function KeyDifferences({
  analysis,
  onAnalyze,
  raceProviders,
}: {
  analysis: AnalysisState
  onAnalyze: AnalyzeHandler
  /** The providers that competed in this run — barred from judging (FR-021). */
  raceProviders: ProviderId[]
}) {
  const [expanded, setExpanded] = useState(true)
  return (
    <section
      aria-label="Diferenças-chave"
      className="overflow-hidden rounded-[var(--radius-panel)] border border-line bg-deck shadow-[inset_0_1px_0_0_rgba(255,255,255,0.04),0_8px_24px_-12px_rgba(0,0,0,0.8)]"
    >
      <header className="flex items-baseline gap-4 px-5 pb-2 pt-4">
        <h2 className="font-mono text-xs tracking-[0.18em] text-ignition">
          DIFERENÇAS-CHAVE
        </h2>
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
          <p className="pb-3 font-mono text-[11px] text-mist">
            análise por IA · respostas anonimizadas e embaralhadas · sem
            vencedor
          </p>

          {analysis.phase === 'idle' && (
            <JudgePicker onAnalyze={onAnalyze} raceProviders={raceProviders} />
          )}

          {analysis.phase === 'streaming' && (
            <div>
              <p className="flex items-center gap-2 pb-2 font-mono text-xs text-ignition">
                <span className="size-1.5 animate-pulse rounded-full bg-current" />
                analisando…
              </p>
              {/* `.streaming` glues the blinking caret to the growing markdown. */}
              <div className="streaming">
                <Markdown>{analysis.text}</Markdown>
              </div>
            </div>
          )}

          {analysis.phase === 'done' && (
            <AnalysisReport analysis={analysis.analysis} />
          )}

          {analysis.phase === 'error' && (
            <div className="flex flex-col gap-3">
              <p role="alert" className="font-body text-sm text-error">
                {analysis.message}
              </p>
              <JudgePicker
                onAnalyze={onAnalyze}
                raceProviders={raceProviders}
              />
            </div>
          )}
        </div>
      )}
    </section>
  )
}
