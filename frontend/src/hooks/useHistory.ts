import { useEffect, useState } from 'react'
import type { ComparisonSummary } from '../types'
import { listComparisons } from '../api/client'

/** Load the signed-in user's past comparisons. */
export function useHistory() {
  const [items, setItems] = useState<ComparisonSummary[] | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    listComparisons().then(setItems, () => setFailed(true))
  }, [])

  return { items, failed }
}
