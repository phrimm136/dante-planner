import { useStore } from 'zustand'
import type { FilterState, FilterStore } from '@/components/hooks/filterStore'

interface FilterEmptyStateProps<T> {
  store: FilterStore<T>
  selectEmpty: (state: FilterState<T>) => boolean
  children: React.ReactNode
}

export function FilterEmptyState<T>({ store, selectEmpty, children }: FilterEmptyStateProps<T>) {
  const empty = useStore(store, selectEmpty)

  if (!empty) return null

  return <>{children}</>
}
