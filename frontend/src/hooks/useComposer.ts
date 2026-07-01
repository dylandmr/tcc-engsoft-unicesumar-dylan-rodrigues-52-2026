import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { ProviderId } from '../types'
import { MAX_PROVIDERS } from '../lib/providers'
import { VALIDATION_MESSAGES, validateComparison } from '../lib/validation'
import { createComparison } from '../api/client'

/** Composer state: prompt, model selection, validation and run dispatch. */
export function useComposer() {
  const navigate = useNavigate()
  const [prompt, setPrompt] = useState('')
  const [selected, setSelected] = useState<ProviderId[]>([])
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const toggle = (id: ProviderId) => {
    setError(null)
    setSelected((prev) => {
      if (prev.includes(id)) return prev.filter((p) => p !== id)
      if (prev.length >= MAX_PROVIDERS) return prev
      return [...prev, id]
    })
  }

  const run = async (event: FormEvent) => {
    event.preventDefault()
    const code = validateComparison(prompt, selected)
    if (code) {
      setError(VALIDATION_MESSAGES[code])
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const created = await createComparison(prompt, selected)
      navigate(`/results/${created.comparisonId}`, {
        state: { providers: created.providers, prompt },
      })
    } catch {
      setError('Não foi possível iniciar a comparação. Tente novamente.')
      setSubmitting(false)
    }
  }

  return {
    prompt,
    setPrompt,
    selected,
    toggle,
    error,
    submitting,
    run,
    full: selected.length >= MAX_PROVIDERS,
  }
}
