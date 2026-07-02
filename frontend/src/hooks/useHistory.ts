import { useEffect, useState } from 'react'
import type { ComparisonSummary } from '../types'
import {
  clearComparisons,
  deleteComparison,
  listComparisons,
} from '../api/client'

/** Load the signed-in user's past comparisons, with FR-022 deletion. */
export function useHistory() {
  const [items, setItems] = useState<ComparisonSummary[] | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    listComparisons().then(setItems, () => setFailed(true))
  }, [])

  /** Delete one comparison and drop its row; rejects (list intact) on failure. */
  const remove = async (id: string) => {
    await deleteComparison(id)
    // Rows only render once items has loaded, so the list is never null here.
    setItems((current) => current!.filter((item) => item.id !== id))
  }

  /** Clear the whole history; rejects (list intact) on failure. */
  const clear = async () => {
    await clearComparisons()
    setItems([])
  }

  return { items, failed, remove, clear }
}
