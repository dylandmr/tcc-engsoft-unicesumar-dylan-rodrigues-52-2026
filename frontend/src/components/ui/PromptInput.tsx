import { Panel } from './Panel'
import { MAX_PROMPT_LEN } from '../../lib/providers'

interface PromptInputProps {
  value: string
  onChange: (value: string) => void
}

/** The prompt textarea with a bottom telemetry footer (char count). */
export function PromptInput({ value, onChange }: PromptInputProps) {
  return (
    <Panel className="flex flex-col gap-4 p-5 transition-colors focus-within:border-ignition/60">
      <textarea
        aria-label="Prompt"
        placeholder="Explique o emaranhamento quântico de forma simples, para uma criança curiosa de 12 anos."
        value={value}
        onChange={(e) => onChange(e.target.value)}
        rows={4}
        className="resize-none bg-transparent font-body text-base text-bright placeholder:text-mist/60 focus:outline-none"
      />
      <div className="flex items-center justify-between font-mono text-xs text-mist">
        <span>um prompt · enviado a cada modelo selecionado</span>
        <span>
          {value.length} / {MAX_PROMPT_LEN}
        </span>
      </div>
    </Panel>
  )
}
