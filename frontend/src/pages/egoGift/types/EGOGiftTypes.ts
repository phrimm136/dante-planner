import type { z } from 'zod'
import type {
  StandardRecipeSchema,
  MixedRecipeSchema,
  EGOGiftRecipeSchema,
  EGOGiftSpecSchema,
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
  EGOGiftSpecListSchema,
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
 * direct boundary shape, so it stays a plain TS type (carries battleKeywordList
 * and an optional name, unlike EGOGiftListItemSchema). Components should prefer
 * EGOGiftName for granular Suspense boundaries.
 */
export interface EGOGiftListItem {
  id: string
  /** Optional - populated when i18n is loaded */
  name?: string
  tag: string[]
  keyword: string | null
  battleKeywordList: string[]
  attributeType: string
  themePack: string[]
  maxEnhancement: 0 | 1 | 2
  hardOnly?: boolean
  extremeOnly?: boolean
  fusioned?: boolean
}

/**
 * @deprecated Use EGOGiftListItem instead. Kept for backwards compatibility.
 */
export interface EGOGiftListItemWithName {
  id: string
  /** Optional - only populated when i18n is loaded. Display uses EGOGiftName component. */
  name?: string
  tag: string[]
  keyword: string | null
  attributeType: string
  themePack: string[]
  maxEnhancement: 0 | 1 | 2
  hardOnly?: boolean
  extremeOnly?: boolean
  fusioned?: boolean
}

export type EGOGiftSpecList = z.infer<typeof EGOGiftSpecListSchema>
export type EGOGiftNameList = z.infer<typeof EGOGiftNameListSchema>
