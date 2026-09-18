import { createStore } from 'zustand'
import type { FilterState, FilterStore } from '@/components/hooks/filterStore'

export function createTestFilterStore<T>(values: T, searchQuery = ''): FilterStore<T> {
  return createStore<FilterState<T>>(() => ({ values, searchQuery }))
}
