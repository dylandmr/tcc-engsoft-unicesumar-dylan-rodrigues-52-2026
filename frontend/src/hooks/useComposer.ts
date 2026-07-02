import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { ModelSelection, ProviderCatalogEntry, ProviderId } from '../types'
import { MAX_PROVIDERS } from '../lib/providers'
import { prefersReducedMotion } from '../lib/motion'
import { VALIDATION_MESSAGES, validateComparison } from '../lib/validation'
import { createComparison, getProviders } from '../api/client'

/** How long the launch overlay holds before navigating to the arena. */
export const LAUNCH_HOLD_MS = 1100

/** Lifecycle of the GET /api/providers catalog fetch. */
export type CatalogState = 'loading' | 'ready' | 'error'

/** Composer state: prompt, provider arming, per-provider model and run dispatch. */
export function useComposer() {
  const navigate = useNavigate()
  const [prompt, setPrompt] = useState('')
  const [selected, setSelected] = useState<ProviderId[]>([])
  // The user's explicit combo-box picks. An armed provider is absent here
  // until they choose — there is no default model anywhere (FR-020).
  const [models, setModels] = useState<ModelSelection>({})
  const [catalog, setCatalog] = useState<
    Partial<Record<ProviderId, ProviderCatalogEntry>>
  >({})
  const [catalogState, setCatalogState] = useState<CatalogState>('loading')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [launching, setLaunching] = useState(false)
  const launchTimer = useRef<number | undefined>(undefined)

  const fetchCatalog = useCallback(() => {
    setCatalogState('loading')
    getProviders().then(
      (entries) => {
        const next: Partial<Record<ProviderId, ProviderCatalogEntry>> = {}
        for (const entry of entries) next[entry.provider] = entry
        setCatalog(next)
        setCatalogState('ready')
        // Disarm providers the fresh catalog no longer offers, and drop
        // picks it no longer lists — the user must choose again explicitly.
        setSelected((prev) =>
          prev.filter((id) => (next[id]?.models.length ?? 0) > 0),
        )
        setModels((prev) => {
          const kept: ModelSelection = {}
          for (const [id, model] of Object.entries(prev) as [
            ProviderId,
            string,
          ][]) {
            if (next[id]?.models.includes(model)) kept[id] = model
          }
          return kept
        })
      },
      () => setCatalogState('error'),
    )
  }, [])

  useEffect(fetchCatalog, [fetchCatalog])

  useEffect(() => () => window.clearTimeout(launchTimer.current), [])

  /** A provider is armable only when the ready catalog offers models for it. */
  const armable = (id: ProviderId) =>
    catalogState === 'ready' && (catalog[id]?.models.length ?? 0) > 0

  /** Why a provider cannot be armed (catalog ready), or null when it can. */
  const unavailableHint = (id: ProviderId): string | null => {
    if (catalogState !== 'ready' || armable(id)) return null
    return catalog[id]?.configured ? 'modelos indisponíveis' : 'não configurado'
  }

  const toggle = (id: ProviderId) => {
    setError(null)
    setSelected((prev) => {
      if (prev.includes(id)) return prev.filter((p) => p !== id)
      if (prev.length >= MAX_PROVIDERS || !armable(id)) return prev
      return [...prev, id]
    })
    // Arming starts with no chosen model; disarming forgets the pick.
    setModels(({ [id]: _dropped, ...rest }) => rest)
  }

  /** The explicit pick for a provider — empty until the user chooses. */
  const modelFor = (id: ProviderId) => models[id] ?? ''

  const setModel = (id: ProviderId, model: string) =>
    setModels((prev) => ({ ...prev, [id]: model }))

  const run = async (event: FormEvent) => {
    event.preventDefault()
    const code = validateComparison(prompt, selected, models)
    if (code) {
      setError(VALIDATION_MESSAGES[code])
      return
    }
    setSubmitting(true)
    setLaunching(true)
    setError(null)
    try {
      // Validation guarantees an explicit pick per armed provider (FR-020).
      const modelMap: ModelSelection = {}
      for (const id of selected) modelMap[id] = models[id]!
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
    catalogState,
    retryCatalog: fetchCatalog,
    armable,
    unavailableHint,
    modelFor,
    setModel,
    error,
    submitting,
    launching,
    run,
    full: selected.length >= MAX_PROVIDERS,
  }
}
