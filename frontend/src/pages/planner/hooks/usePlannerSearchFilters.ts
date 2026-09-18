import { useUrlFilters } from '@/components/hooks/useUrlFilters'

import type { PlannerSearchFilters, PlannerSearchParams } from '../types/PlannerSearchTypes'

function parseCsvParam(value: string | undefined): string[] {
  if (!value || value.trim() === '') return []
  return value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

function toCsvParam(values: string[]): string | undefined {
  return values.length > 0 ? values.join(',') : undefined
}

type PlannerSearchParamsUpdate = {
  [K in keyof PlannerSearchParams]: PlannerSearchParams[K] | undefined
}

export interface UsePlannerSearchFiltersResult {
  filters: PlannerSearchFilters
  hasActiveFilters: boolean
  setFilters: (updates: Partial<PlannerSearchFilters>) => void
  clearFilters: () => void
}

export function usePlannerSearchFilters(): UsePlannerSearchFiltersResult {
  const { params: search, setParams } = useUrlFilters<PlannerSearchParamsUpdate>()

  const filters: PlannerSearchFilters = {
    title: search?.q || null,
    keywords: parseCsvParam(search?.keyword),
    identityIds: parseCsvParam(search?.identity),
    egoIds: parseCsvParam(search?.ego),
    giftIds: parseCsvParam(search?.gift),
    themePackIds: parseCsvParam(search?.themePack),
  }

  const hasActiveFilters =
    filters.title !== null ||
    filters.keywords.length > 0 ||
    filters.identityIds.length > 0 ||
    filters.egoIds.length > 0 ||
    filters.giftIds.length > 0 ||
    filters.themePackIds.length > 0

  const setFilters = (updates: Partial<PlannerSearchFilters>) => {
    const merged = { ...filters, ...updates }

    setParams({
      q: merged.title || undefined,
      keyword: toCsvParam(merged.keywords),
      identity: toCsvParam(merged.identityIds),
      ego: toCsvParam(merged.egoIds),
      gift: toCsvParam(merged.giftIds),
      themePack: toCsvParam(merged.themePackIds),
    })
  }

  const clearFilters = () => {
    setParams({
      q: undefined,
      keyword: undefined,
      identity: undefined,
      ego: undefined,
      gift: undefined,
      themePack: undefined,
    })
  }

  return {
    filters,
    hasActiveFilters,
    setFilters,
    clearFilters,
  }
}
