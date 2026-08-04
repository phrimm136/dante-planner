/**
 * themePackFilter.ts
 *
 * Facet descriptors for the theme pack browser, plus the per-item predicate the grid's
 * card slots subscribe through.
 */

import type { z } from 'zod'
import type { Facet } from '@/shared/filter'
import { applyFacets } from '@/shared/filter'
import type { FilterState } from '@/components/hooks/useSetFilters'
import type { DungeonIdx, ThemePackFloor } from '@/shared/gameData'
import type { ThemePackI18nSchema } from '../schemas/ThemePackSchemas'
import type { ThemePackEntry } from '../types/ThemePackTypes'

export interface ThemePackFacetState {
  selectedDifficulties: ReadonlySet<DungeonIdx>
  selectedFloors: ReadonlySet<ThemePackFloor>
  selectedEgoGifts: ReadonlySet<string>
}

export const THEME_PACK_FACETS: readonly Facet<ThemePackEntry, ThemePackFacetState>[] = [
  {
    sel: (s) => s.selectedDifficulties,
    get: (e) => e.exceptionConditions.map((c) => c.dungeonIdx),
    mode: 'all',
  },
  {
    sel: (s) => s.selectedFloors,
    get: (e) => e.exceptionConditions.flatMap((c) => c.selectableFloors ?? []),
    mode: 'all',
  },
  {
    sel: (s) => s.selectedEgoGifts,
    get: (e) => [...e.specificEgoGiftPool, ...(e.fixedRewardEgoGifts ?? [])].map(String),
    mode: 'any',
  },
]

/**
 * Every lowercased string the search box matches a theme pack on: its localized name.
 *
 * Depends only on the i18n payload, so a filter toggle never invalidates it.
 */
export function buildThemePackSearchTerms(
  packId: string,
  themePackI18n: z.infer<typeof ThemePackI18nSchema>,
): string[] {
  return [(themePackI18n[packId]?.name ?? '').toLowerCase()]
}

/** Whether one theme pack survives the current facets and search query. */
export function matchesThemePack(
  entry: ThemePackEntry,
  state: FilterState<ThemePackFacetState>,
  searchTerms: readonly string[],
): boolean {
  if (!applyFacets(entry, state.values, THEME_PACK_FACETS)) return false
  if (!state.searchQuery) return true

  const lowerQuery = state.searchQuery.toLowerCase()
  return searchTerms.some((term) => term.includes(lowerQuery))
}
