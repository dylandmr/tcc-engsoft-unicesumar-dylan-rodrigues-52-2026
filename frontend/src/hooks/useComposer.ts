import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { ModelSelection, ProviderCatalogEntry, ProviderId } from '../types'
import { MAX_PROVIDERS, fallbackCatalog } from '../lib/providers'
import { prefersReducedMotion } from '../lib/motion'
import { VALIDATION_MESSAGES, validateComparison } from '../lib/validation'
import { createComparison, getProviders } from '../api/client'

/** How long the launch overlay holds before navigating to the arena. */
export const LAUNCH_HOLD_MS = 1100

/** Composer state: prompt, provider arming, per-provider model and run dispatch. */
export function useComposer() {
  const navigate = useNavigate()
  const [prompt, setPrompt] = useState('')
  const [selected, setSelected] = useState<ProviderId[]>([])
  // Explicit combo-box picks only; an armed provider without an entry runs
  // its catalog default, so arming/disarming needs no model bookkeeping.
  const [models, setModels] = useState<ModelSelection>({})
  const [catalog, setCatalog] =
    useState<Record<ProviderId, ProviderCatalogEntry>>(fallbackCatalog)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [launching, setLaunching] = useState(false)
  const launchTimer = useRef<number | undefined>(undefined)

  useEffect(() => {
    getProviders().then(
      (entries) => {
        const next = fallbackCatalog()
        for (const entry of entries) next[entry.provider] = entry
        setCatalog(next)
        // Drop explicit picks the fresh catalog no longer lists — they fall
        // back to that provider's (new) default.
        setModels((prev) => {
          const kept: ModelSelection = {}
          for (const [id, model] of Object.entries(prev) as [
            ProviderId,
            string,
          ][]) {
            if (next[id].models.includes(model)) kept[id] = model
          }
          return kept
        })
      },
      () => {
        // Catalog failure never blocks composing — the static fallback stands.
      },
    )
  }, [])

  useEffect(() => () => window.clearTimeout(launchTimer.current), [])

  const toggle = (id: ProviderId) => {
    setError(null)
    setSelected((prev) => {
      if (prev.includes(id)) return prev.filter((p) => p !== id)
      if (prev.length >= MAX_PROVIDERS) return prev
      return [...prev, id]
    })
    // Arming starts at the catalog default; disarming forgets the pick.
    setModels(({ [id]: _dropped, ...rest }) => rest)
  }

  /** Effective model for a provider: explicit pick, else catalog default. */
  const modelFor = (id: ProviderId) => models[id] ?? catalog[id].defaultModel

  const setModel = (id: ProviderId, model: string) =>
    setModels((prev) => ({ ...prev, [id]: model }))

  const run = async (event: FormEvent) => {
    event.preventDefault()
    const code = validateComparison(prompt, selected)
    if (code) {
      setError(VALIDATION_MESSAGES[code])
      return
    }
    setSubmitting(true)
    setLaunching(true)
    setError(null)
    try {
      const modelMap: ModelSelection = {}
      for (const id of selected) modelMap[id] = modelFor(id)
      const created = await createComparison(prompt, selected, modelMap)
      const openArena = () =>
        navigate(`/results/${created.comparisonId}`, {
          state: { providers: created.providers, prompt, models: modelMap },
        })
      // Launch sequence: hold on the ignition overlay for a beat — unless the
      // OS asks for reduced motion, then cut straight to the arena.
      if (prefersReducedMotion()) {
        openArena()
        return
      }
      launchTimer.current = window.setTimeout(openArena, LAUNCH_HOLD_MS)
    } catch {
      setError('Não foi possível iniciar a comparação. Tente novamente.')
      setSubmitting(false)
      setLaunching(false)
    }
  }

  return {
    prompt,
    setPrompt,
    selected,
    toggle,
    catalog,
    modelFor,
    setModel,
    error,
    submitting,
    launching,
    run,
    full: selected.length >= MAX_PROVIDERS,
  }
}
