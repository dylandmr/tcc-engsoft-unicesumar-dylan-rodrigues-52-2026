import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { ComparisonSummary, ProviderStats } from '../types'
import { useHistory } from '../hooks/useHistory'
import { useSession } from '../auth/SessionContext'
import { TopBar } from '../components/ui/TopBar'
import { Panel } from '../components/ui/Panel'
import { PROVIDER_STYLES } from '../lib/providerStyles'
import { providerMeta } from '../lib/providers'
import {
  formatLatency,
  formatTokensPerSecond,
  relativeTime,
} from '../lib/format'
import { cn } from '../lib/cn'

const DELETE_FAILED = 'Não foi possível apagar. Tente novamente.'

function HistoryRow({
  item,
  onDelete,
}: {
  item: ComparisonSummary
  onDelete: (id: string) => Promise<void>
}) {
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const [failed, setFailed] = useState(false)
  const open = () =>
    navigate(`/results/${item.id}`, {
      state: { providers: item.providers, prompt: item.prompt },
    })
  const confirmDelete = async () => {
    setConfirming(false)
    setFailed(false)
    try {
      await onDelete(item.id)
    } catch {
      setFailed(true)
    }
  }
  return (
    <li className="rounded-[var(--radius-panel)] border border-line bg-deck px-5 py-4 shadow-[inset_0_1px_0_0_rgba(255,255,255,0.04),0_8px_24px_-12px_rgba(0,0,0,0.8)] transition duration-150 hover:-translate-y-0.5 hover:border-mist">
      <div className="flex items-center gap-4">
        <button onClick={open} className="min-w-0 flex-1 rounded-md text-left">
          <span className="block truncate font-body text-bright">
            {item.prompt}
          </span>
          <span className="mt-2 flex items-center gap-2 font-mono text-xs text-mist">
            {item.providers.map((p) => (
              <span
                key={p}
                className={cn('size-2 rounded-full', PROVIDER_STYLES[p].dot)}
              />
            ))}
            {item.providers.length} modelos
          </span>
        </button>
        <span className="shrink-0 font-mono text-xs text-mist">
          {relativeTime(item.createdAt)}
        </span>
        {confirming ? (
          <span className="flex shrink-0 items-center gap-3 font-mono text-xs">
            <button
              onClick={confirmDelete}
              aria-label={`confirmar exclusão de "${item.prompt}"`}
              className="text-error transition hover:brightness-125"
            >
              confirmar
            </button>
            <button
              onClick={() => setConfirming(false)}
              aria-label={`cancelar exclusão de "${item.prompt}"`}
              className="text-mist transition hover:text-bright"
            >
              cancelar
            </button>
          </span>
        ) : (
          <button
            onClick={() => {
              setConfirming(true)
              setFailed(false)
            }}
            aria-label={`apagar "${item.prompt}"`}
            className="shrink-0 font-mono text-xs text-mist transition hover:text-error"
          >
            apagar
          </button>
        )}
      </div>
      {failed && (
        <p role="alert" className="mt-2 font-mono text-xs text-error">
          {DELETE_FAILED}
        </p>
      )}
    </li>
  )
}

/** "N corrida(s)" / "N vazia(s)" / "N erro(s)" / "N timeout(s)". */
function count(n: number, singular: string, plural: string): string {
  return `${n} ${n === 1 ? singular : plural}`
}

/** Outcome breakdown, e.g. "7 ok · 1 erro" — zero non-success categories hidden. */
function outcomeLine(stats: ProviderStats): string {
  const parts = [`${stats.successes} ok`]
  if (stats.empties > 0) parts.push(count(stats.empties, 'vazia', 'vazias'))
  if (stats.errors > 0) parts.push(count(stats.errors, 'erro', 'erros'))
  if (stats.timeouts > 0)
    parts.push(count(stats.timeouts, 'timeout', 'timeouts'))
  return parts.join(' · ')
}

/** One metric of a stats card: mono label over the averaged readout (or "—"). */
function StatMetric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[10px] uppercase tracking-[0.18em] text-mist">
        {label}
      </dt>
      <dd className="mt-0.5 text-bright">{value}</dd>
    </div>
  )
}

/** One provider's aggregate card in the "ESTATÍSTICAS" strip (FR-023). */
function StatsCard({ stats }: { stats: ProviderStats }) {
  const meta = providerMeta(stats.provider)
  return (
    <Panel
      role="group"
      aria-label={`estatísticas de ${meta.label}`}
      className="px-4 py-3"
    >
      <div className="flex items-baseline gap-2">
        <span
          className={cn(
            'size-2 self-center rounded-full',
            PROVIDER_STYLES[stats.provider].dot,
          )}
        />
        <span className="font-display text-sm font-bold text-bright">
          {meta.label}
        </span>
        <span className="ml-auto shrink-0 font-mono text-xs text-mist">
          {count(stats.runs, 'corrida', 'corridas')}
        </span>
      </div>
      <p className="mt-1 font-mono text-xs text-mist">{outcomeLine(stats)}</p>
      <dl className="mt-3 grid grid-cols-3 gap-2 font-mono text-xs">
        <StatMetric
          label="latência"
          value={
            stats.avgResponseTimeMs !== null
              ? formatLatency(stats.avgResponseTimeMs)
              : '—'
          }
        />
        <StatMetric
          label="1º token"
          value={
            stats.avgFirstTokenMs !== null
              ? formatLatency(stats.avgFirstTokenMs)
              : '—'
          }
        />
        <StatMetric
          label="tok/s"
          value={
            stats.avgTokensPerSecond !== null
              ? formatTokensPerSecond(stats.avgTokensPerSecond)
              : '—'
          }
        />
      </dl>
      <p className="mt-2 font-mono text-[10px] text-mist/80">
        telemetria em {stats.telemetryRuns} de {stats.runs} execuções
      </p>
    </Panel>
  )
}

/** Per-user history of past comparisons (US4 / FR-015, FR-017) with deletion (FR-022). */
export function HistoryPage() {
  const { items, stats, failed, remove, clear } = useHistory()
  const { signOut } = useSession()
  const navigate = useNavigate()
  const [clearConfirming, setClearConfirming] = useState(false)
  const [clearFailed, setClearFailed] = useState(false)
  const handleSignOut = async () => {
    await signOut()
    navigate('/login')
  }
  const confirmClear = async () => {
    setClearConfirming(false)
    setClearFailed(false)
    try {
      await clear()
    } catch {
      setClearFailed(true)
    }
  }
  const hasEntries = items !== null && items.length > 0

  return (
    <div className="min-h-screen">
      <TopBar>
        <Link to="/" className="text-ignition hover:brightness-110">
          nova comparação
        </Link>
        <button onClick={handleSignOut} className="hover:text-bright">
          sair
        </button>
      </TopBar>

      <main className="page-in mx-auto max-w-4xl px-6 pt-16">
        <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="font-mono text-xs tracking-[0.18em] text-ignition">
              HISTÓRICO
            </p>
            <h1 className="mt-2 font-display text-4xl font-medium text-bright">
              Comparações anteriores
            </h1>
          </div>
          {hasEntries &&
            (clearConfirming ? (
              <span className="flex items-center gap-3 font-mono text-xs">
                <span className="text-bright">
                  apagar todas as comparações?
                </span>
                <button
                  onClick={confirmClear}
                  className="text-error transition hover:brightness-125"
                >
                  confirmar
                </button>
                <button
                  onClick={() => setClearConfirming(false)}
                  className="text-mist transition hover:text-bright"
                >
                  cancelar
                </button>
              </span>
            ) : (
              <button
                onClick={() => {
                  setClearConfirming(true)
                  setClearFailed(false)
                }}
                className="font-mono text-xs text-mist transition hover:text-bright"
              >
                limpar histórico
              </button>
            ))}
        </div>

        {stats.length > 0 && (
          <section aria-label="Estatísticas" className="mb-8">
            <p className="font-mono text-xs tracking-[0.18em] text-ignition">
              ESTATÍSTICAS
            </p>
            <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {stats.map((entry) => (
                <StatsCard key={entry.provider} stats={entry} />
              ))}
            </div>
          </section>
        )}

        {clearFailed && (
          <p role="alert" className="mb-4 font-mono text-xs text-error">
            {DELETE_FAILED}
          </p>
        )}

        {failed ? (
          <p role="alert" className="font-body text-error">
            Não foi possível carregar o histórico.
          </p>
        ) : items === null ? (
          <p className="font-body text-mist">Carregando…</p>
        ) : items.length === 0 ? (
          <Panel className="p-8 text-center font-body text-mist">
            Nenhuma comparação ainda. Faça a primeira.
          </Panel>
        ) : (
          <ul className="flex flex-col gap-3">
            {items.map((item) => (
              <HistoryRow key={item.id} item={item} onDelete={remove} />
            ))}
          </ul>
        )}
      </main>
    </div>
  )
}
