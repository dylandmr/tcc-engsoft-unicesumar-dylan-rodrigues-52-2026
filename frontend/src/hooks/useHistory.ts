import { useCallback, useEffect, useState } from 'react'
import type { ComparisonSummary, ProviderStats } from '../types'
import {
  clearComparisons,
  deleteComparison,
  getStats,
  listComparisons,
} from '../api/client'

/**
 * Load the signed-in user's past comparisons, with FR-022 deletion and the
 * FR-023 per-provider aggregates. Stats are an enhancement, never a gate:
 * a stats failure leaves them empty and the list untouched.
 */
export function useHistory() {
  const [items, setItems] = useState<ComparisonSummary[] | null>(null)
  const [stats, setStats] = useState<ProviderStats[]>([])
  const [failed, setFailed] = useState(false)

  /** Re-derive the aggregates from the live endpoint; empty on failure. */
  const refreshStats = useCallback(
    () => getStats().then(setStats, () => setStats([])),
    [],
  )

  useEffect(() => {
    listComparisons().then(setItems, () => setFailed(true))
    void refreshStats()
  }, [refreshStats])

  /** Delete one comparison and drop its row; rejects (list intact) on failure. */
  const remove = async (id: string) => {
    await deleteComparison(id)
    // Rows only render once items has loaded, so the list is never null here.
    setItems((current) => current!.filter((item) => item.id !== id))
    await refreshStats()
  }

  /** Clear the whole history; rejects (list intact) on failure. */
  const clear = async () => {
    await clearComparisons()
    setItems([])
    await refreshStats()
  }

  return { items, stats, failed, remove, clear }
}
