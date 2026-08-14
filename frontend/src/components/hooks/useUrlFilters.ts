import { useCallback } from 'react'
import { useSearch, useNavigate } from '@tanstack/react-router'

export interface UrlFilters<TParams> {
  /** Current search params, or undefined before the route has any. */
  params: Partial<TParams> | undefined
  /** Merges updates into the current params; an undefined value drops its key. */
  setParams: (updates: Partial<TParams>) => void
  /** Drops every search param on the route. */
  clearParams: () => void
}

/**
 * Reads and writes a route's search params as one filter object.
 *
 * `useSearch({ strict: false })` cannot know the route's schema, so the shape is
 * asserted — here once, rather than at each hook that wraps it.
 */
export function useUrlFilters<TParams>(): UrlFilters<TParams> {
  const params = useSearch({ strict: false }) as Partial<TParams> | undefined
  const navigate = useNavigate()

  const setParams = useCallback(
    (updates: Partial<TParams>) => {
      void navigate({
        to: '.',
        search: (prev) => ({ ...prev, ...updates }),
        replace: false,
      })
    },
    [navigate],
  )

  const clearParams = useCallback(() => {
    void navigate({ to: '.', search: {}, replace: false })
  }, [navigate])

  return { params, setParams, clearParams }
}
