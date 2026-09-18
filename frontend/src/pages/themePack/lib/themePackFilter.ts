import type { z } from 'zod'
import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import type { DungeonIdx, ThemePackFloor } from '@/shared/gameData'
import type { ThemePackI18nSchema } from '../schemas/ThemePackSchemas'
import type { ThemePackEntity } from '../types/ThemePackTypes'

export interface ThemePackFacetState {
  selectedDifficulties: ReadonlySet<DungeonIdx>
  selectedFloors: ReadonlySet<ThemePackFloor>
  selectedEgoGifts: ReadonlySet<string>
}

export const THEME_PACK_FACETS: readonly Facet<ThemePackEntity, ThemePackFacetState>[] = [
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

export function buildThemePackSearchTerms(
  packId: string,
  themePackI18n: z.infer<typeof ThemePackI18nSchema>,
): string[] {
  return [(themePackI18n[packId]?.name ?? '').toLowerCase()]
}

export const matchesThemePack: EntityMatcher<ThemePackEntity, ThemePackFacetState> =
  createEntityMatcher(THEME_PACK_FACETS)
