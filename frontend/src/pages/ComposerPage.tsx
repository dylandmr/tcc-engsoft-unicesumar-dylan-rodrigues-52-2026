import { Link, useNavigate } from 'react-router-dom'
import { useComposer } from '../hooks/useComposer'
import { useSession } from '../auth/SessionContext'
import { PROVIDERS } from '../lib/providers'
import { Backdrop } from '../components/ui/Backdrop'
import { TopBar } from '../components/ui/TopBar'
import { PromptInput } from '../components/ui/PromptInput'
import { ProviderChip } from '../components/ui/ProviderChip'
import { Button } from '../components/ui/Button'

/** New-comparison composer: prompt + model picker + run (US1). */
export function ComposerPage() {
  const c = useComposer()
  const { signOut } = useSession()
  const navigate = useNavigate()
  const handleSignOut = async () => {
    await signOut()
    navigate('/login')
  }

  return (
    <div className="min-h-screen">
      <Backdrop />
      <TopBar>
        <Link to="/history" className="hover:text-bright">
          history
        </Link>
        <button onClick={handleSignOut} className="hover:text-bright">
          sign out
        </button>
      </TopBar>

      <main className="mx-auto max-w-3xl px-6 pt-16">
        <p className="font-mono text-xs tracking-wider text-ignition">
          NEW COMPARISON
        </p>
        <h1 className="mt-3 font-display text-4xl font-medium text-bright">
          What should the models answer?
        </h1>

        <form onSubmit={c.run} className="mt-6 flex flex-col gap-6">
          <PromptInput value={c.prompt} onChange={c.setPrompt} />

          <div>
            <p className="font-mono text-xs tracking-wider text-mist">
              SELECT UP TO 4 MODELS · {c.selected.length} CHOSEN
            </p>
            <div className="mt-3 flex flex-wrap gap-3">
              {PROVIDERS.map((meta) => {
                const selected = c.selected.includes(meta.id)
                return (
                  <ProviderChip
                    key={meta.id}
                    meta={meta}
                    selected={selected}
                    disabled={!selected && c.full}
                    onToggle={() => c.toggle(meta.id)}
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
            <Button type="submit" disabled={c.submitting}>
              Run comparison →
            </Button>
            <span className="font-mono text-xs text-mist">
              streamed live, side by side
            </span>
          </div>
        </form>
      </main>
    </div>
  )
}
