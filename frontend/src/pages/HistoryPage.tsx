import { Link, useNavigate } from 'react-router-dom'
import type { ComparisonSummary } from '../types'
import { useHistory } from '../hooks/useHistory'
import { useSession } from '../auth/SessionContext'
import { TopBar } from '../components/ui/TopBar'
import { Panel } from '../components/ui/Panel'
import { PROVIDER_STYLES } from '../lib/providerStyles'
import { relativeTime } from '../lib/format'
import { cn } from '../lib/cn'

function HistoryRow({ item }: { item: ComparisonSummary }) {
  const navigate = useNavigate()
  const open = () =>
    navigate(`/results/${item.id}`, {
      state: { providers: item.providers, prompt: item.prompt },
    })
  return (
    <li>
      <button
        onClick={open}
        className="flex w-full items-center justify-between gap-4 rounded-[var(--radius-panel)] border border-line bg-deck px-5 py-4 text-left hover:border-mist"
      >
        <span className="min-w-0">
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
        </span>
        <span className="shrink-0 font-mono text-xs text-mist">
          {relativeTime(item.createdAt)}
        </span>
      </button>
    </li>
  )
}

/** Per-user history of past comparisons (US4 / FR-015, FR-017). */
export function HistoryPage() {
  const { items, failed } = useHistory()
  const { signOut } = useSession()
  const navigate = useNavigate()
  const handleSignOut = async () => {
    await signOut()
    navigate('/login')
  }

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

      <main className="mx-auto max-w-4xl px-6">
        <p className="font-mono text-xs tracking-wider text-ignition">
          HISTÓRICO
        </p>
        <h1 className="mt-2 mb-8 font-display text-4xl font-medium text-bright">
          Comparações anteriores
        </h1>

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
              <HistoryRow key={item.id} item={item} />
            ))}
          </ul>
        )}
      </main>
    </div>
  )
}
