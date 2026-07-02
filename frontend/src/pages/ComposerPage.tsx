import { Link, useNavigate } from 'react-router-dom'
import { useComposer } from '../hooks/useComposer'
import { useSession } from '../auth/SessionContext'
import { PROVIDERS } from '../lib/providers'
import { Backdrop } from '../components/ui/Backdrop'
import { TopBar } from '../components/ui/TopBar'
import { PromptInput } from '../components/ui/PromptInput'
import { ContenderCard } from '../components/ui/ContenderCard'
import { Button } from '../components/ui/Button'

/**
 * New-comparison composer (US1): prompt + the "grid de largada" of provider
 * contender cards, each armed card carrying its model combo box (FR-020).
 */
export function ComposerPage() {
  const c = useComposer()
  const { signOut } = useSession()
  const navigate = useNavigate()
  const handleSignOut = async () => {
    await signOut()
    navigate('/login')
  }
  const chosen = c.selected.length

  return (
    <div className="relative min-h-screen">
      <Backdrop />
      {/* Ambient dot grid behind the heading (mock 02b). */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-10 top-24 mx-auto h-44 max-w-4xl"
        style={{
          backgroundImage:
            'radial-gradient(circle, rgba(255,255,255,0.045) 1px, transparent 1.6px)',
          backgroundSize: '36px 36px',
        }}
      />
      <TopBar>
        <Link to="/history" className="hover:text-bright">
          histórico
        </Link>
        <button onClick={handleSignOut} className="hover:text-bright">
          sair
        </button>
      </TopBar>

      <main className="page-in relative mx-auto max-w-5xl px-6 pb-16 pt-10">
        <div className="text-center">
          <p className="font-mono text-xs font-medium uppercase tracking-[0.34em] text-ignition">
            Nova comparação
          </p>
          <h1 className="mx-auto mt-4 max-w-2xl font-display text-5xl font-medium leading-[1.06] tracking-tight text-bright md:text-6xl">
            O que os modelos devem responder?
          </h1>
        </div>

        <form onSubmit={c.run} className="mt-12 flex flex-col gap-10">
          <PromptInput value={c.prompt} onChange={c.setPrompt} />

          <div>
            <div className="flex items-center justify-between font-mono text-xs tracking-[0.14em]">
              <span className="text-mist">SELECIONE ATÉ 4 MODELOS</span>
              <span className="text-bright">
                {`${chosen} ${chosen === 1 ? 'ESCOLHIDO' : 'ESCOLHIDOS'}`}
              </span>
            </div>
            <div className="mt-3.5 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
              {PROVIDERS.map((meta) => {
                const entry = c.catalog[meta.id]
                const order = c.selected.indexOf(meta.id)
                return (
                  <ContenderCard
                    key={meta.id}
                    meta={meta}
                    order={order}
                    configured={entry.configured}
                    disabled={order < 0 && c.full}
                    model={c.modelFor(meta.id)}
                    models={entry.models}
                    defaultModel={entry.defaultModel}
                    onToggle={() => c.toggle(meta.id)}
                    onModelChange={(model) => c.setModel(meta.id, model)}
                  />
                )
              })}
            </div>
          </div>

          {c.error && (
            <p role="alert" className="font-body text-sm text-error">
              {c.error}
            </p>
          )}

          <div className="flex items-center justify-between">
            <Button
              type="submit"
              disabled={c.submitting}
              className="font-display text-lg font-bold"
            >
              Comparar →
            </Button>
            <span className="font-mono text-xs text-mist">
              transmitido ao vivo, lado a lado
            </span>
          </div>
        </form>
      </main>

      {c.launching && (
        <div
          role="status"
          className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-6 bg-void/80"
        >
          <p className="font-mono text-base font-medium tracking-[0.24em] text-ignition">
            {`INICIANDO TRANSMISSÃO · ${chosen} ${chosen === 1 ? 'MODELO' : 'MODELOS'}`}
          </p>
          <div className="h-0.5 w-80 overflow-hidden bg-ignition/15">
            <div className="h-full w-full origin-left animate-[launch-fill_1.1s_ease-in-out_forwards] bg-ignition shadow-[0_0_10px_0] shadow-ignition/70" />
          </div>
        </div>
      )}
    </div>
  )
}
