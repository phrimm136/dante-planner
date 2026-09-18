import { useUrlFilters } from '@/components/hooks/useUrlFilters'

import type { MDCategory } from '@/shared/gameData'
import type { MDUserSearchParams } from '../types/MDPlannerListTypes'

const DEFAULT_PAGE = 0

export interface UseMDUserFiltersResult {
  category: MDCategory | undefined
  page: number
  search: string
  setFilters: (updates: Partial<MDUserSearchParams>) => void
  clearFilters: () => void
  resetPage: () => void
}

export function useMDUserFilters(): UseMDUserFiltersResult {
  const {
    params: search,
    setParams: setFilters,
    clearParams: clearFilters,
  } = useUrlFilters<MDUserSearchParams>()

  const category = search?.category
  const page = search?.page ?? DEFAULT_PAGE
  const searchQuery = search?.q ?? ''

  const resetPage = () => {
    if (page !== DEFAULT_PAGE) {
      setFilters({ page: DEFAULT_PAGE })
    }
  }

  return {
    category,
    page,
    search: searchQuery,
    setFilters,
    clearFilters,
    resetPage,
  }
}
