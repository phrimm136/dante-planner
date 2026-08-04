import { useStore } from 'zustand'
import type { FilterState, FilterStore } from '@/components/hooks/useSetFilters'

interface FilterEmptyStateProps<T> {
  store: FilterStore<T>
  /** Whether the current filters leave the list with nothing to show */
  selectEmpty: (state: FilterState<T>) => boolean
  children: React.ReactNode
}

/**
 * Renders its children only while the current filters match no item.
 *
 * Subscribed rather than prop-fed so the grid does not re-render to hand a list its
 * emptiness, and mounted alongside the grid rather than replacing it, so recovering
 * from an empty result does not remount every card.
 */
export function FilterEmptyState<T>({ store, selectEmpty, children }: FilterEmptyStateProps<T>) {
  const empty = useStore(store, selectEmpty)

  if (!empty) return null

  return <>{children}</>
}
