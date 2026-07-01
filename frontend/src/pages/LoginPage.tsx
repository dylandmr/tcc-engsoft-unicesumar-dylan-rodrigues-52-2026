import { useLogin } from '../hooks/useLogin'
import { Backdrop } from '../components/ui/Backdrop'
import { Panel } from '../components/ui/Panel'
import { Button } from '../components/ui/Button'
import { Logo } from '../components/ui/Logo'

/** Sign-in screen (FR-002, FR-003). */
export function LoginPage() {
  const f = useLogin()
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-6">
      <Backdrop />
      <div className="mb-8 flex flex-col items-center gap-2 text-center">
        <Logo />
        <p className="font-body text-sm text-mist">
          Avaliação paralela e lado a lado de IAs generativas.
        </p>
      </div>

      <Panel className="w-full max-w-md p-6">
        <p className="mb-4 font-mono text-xs tracking-wider text-ignition">
          ENTRAR
        </p>
        <form onSubmit={f.submit} className="flex flex-col gap-3">
          <input
            aria-label="Usuário"
            placeholder="usuário"
            value={f.username}
            onChange={(e) => f.setUsername(e.target.value)}
            className="rounded-lg border border-line bg-void px-4 py-3 font-body text-bright placeholder:text-mist focus:border-ignition focus:outline-none"
          />
          <input
            aria-label="Senha"
            type="password"
            placeholder="senha"
            value={f.password}
            onChange={(e) => f.setPassword(e.target.value)}
            className="rounded-lg border border-line bg-void px-4 py-3 font-body text-bright placeholder:text-mist focus:border-ignition focus:outline-none"
          />
          {f.error && (
            <p role="alert" className="font-body text-sm text-error">
              {f.error}
            </p>
          )}
          <Button type="submit" disabled={f.submitting} className="mt-1">
            Entrar na arena →
          </Button>
        </form>
      </Panel>

      <p className="mt-6 font-mono text-xs text-mist">
        protótipo local · seu histórico fica nesta máquina
      </p>
    </main>
  )
}
