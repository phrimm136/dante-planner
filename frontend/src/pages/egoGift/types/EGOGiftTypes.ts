import type { z } from 'zod'
import type { EGOGiftId } from '@/shared/gameData'
import type {
  StandardRecipeSchema,
  MixedRecipeSchema,
  EGOGiftRecipeSchema,
  EGOGiftSpecSchema,
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
  EGOGiftNameListSchema,
} from '../schemas/EGOGiftSchemas'

export type StandardRecipe = z.infer<typeof StandardRecipeSchema>
export type MixedRecipe = z.infer<typeof MixedRecipeSchema>
export type EGOGiftRecipe = z.infer<typeof EGOGiftRecipeSchema>

export type EGOGiftSpec = z.infer<typeof EGOGiftSpecSchema>
export type EGOGiftData = z.infer<typeof EGOGiftDataSchema>
export type EGOGiftI18n = z.infer<typeof EGOGiftI18nSchema>

/**
 * EGO Gift list item for list/grid views.
 *
 * Assembled in list components from already-validated spec + name list — not a
 * direct boundary shape, so it stays a plain TS type. Components should prefer
 * EGOGiftName for granular Suspense boundaries.
 */
export interface EGOGiftListItem {
  id: EGOGiftId
  /** Optional - populated when i18n is loaded */
  name?: string
  tag: string[]
  keyword: string | null
  battleKeywordList: string[]
  attributeType: string
  themePack: string[]
  maxEnhancement: 0 | 1 | 2
  recipe?: EGOGiftRecipe
  hardOnly?: boolean
  extremeOnly?: boolean
  fusioned?: boolean
}

export type EGOGiftNameList = z.infer<typeof EGOGiftNameListSchema>
