import { useUrlFilters } from '@/components/hooks/useUrlFilters'

import type {
  MDGesellschaftFilters,
  MDGesellschaftMode,
  MDGesellschaftSearchParams,
} from '../types/MDPlannerListTypes'

const DEFAULT_PAGE = 0
const DEFAULT_MODE: MDGesellschaftMode = 'published'

export interface UseMDGesellschaftFiltersResult {
  filters: MDGesellschaftFilters
  setFilters: (updates: Partial<MDGesellschaftSearchParams>) => void
  clearFilters: () => void
  resetPage: () => void
  showBest: () => void
  showAll: () => void
}

export function useMDGesellschaftFilters(): UseMDGesellschaftFiltersResult {
  const {
    params: search,
    setParams: setFilters,
    clearParams: clearFilters,
  } = useUrlFilters<MDGesellschaftSearchParams>()

  const category = search?.category
  const page = search?.page ?? DEFAULT_PAGE
  const mode = search?.mode ?? DEFAULT_MODE
  const searchQuery = search?.q ?? ''
  const keyword = search?.keyword
  const identity = search?.identity
  const ego = search?.ego
  const gift = search?.gift
  const themePack = search?.themePack

  const resetPage = () => {
    if (page !== DEFAULT_PAGE) {
      setFilters({ page: DEFAULT_PAGE })
    }
  }

  const showBest = () => {
    setFilters({ mode: 'best', page: 0 })
  }

  const showAll = () => {
    setFilters({ mode: 'published', page: 0 })
  }

  return {
    filters: {
      category,
      page,
      mode,
      search: searchQuery,
      keyword,
      identity,
      ego,
      gift,
      themePack,
    },
    setFilters,
    clearFilters,
    resetPage,
    showBest,
    showAll,
  }
}
