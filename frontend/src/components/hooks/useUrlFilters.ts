import { useCallback } from 'react'
import { useSearch, useNavigate } from '@tanstack/react-router'

export interface UrlFilters<TParams> {
  params: Partial<TParams> | undefined
  setParams: (updates: Partial<TParams>) => void
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
