// Recipe types for EGO Gift fusion/combination
// Standard recipe: multiple recipe options, each with fixed ingredient IDs
export interface StandardRecipe {
  materials: number[][]
}

// Mixed recipe (Lunar Memory only): pick N from pool A + M from pool B
export interface MixedRecipe {
  type: 'mixed'
  a: { ids: number[]; count: number }
  b: { ids: number[]; count: number }
}

// Union type for all recipe formats
export type EGOGiftRecipe = StandardRecipe | MixedRecipe

// Raw data structure from egoGiftSpecList.json
// tag array must contain at least one "TIER_*" string (validated by Zod schema at runtime)
export interface EGOGiftSpec {
  tag: string[]
  keyword: string | null
  attributeType: string
  themePack: string[]
  hardOnly?: boolean
  extremeOnly?: boolean
  recipe?: EGOGiftRecipe
}

// Raw data structure from static/data/egoGift/{id}.json
// tag array must contain at least one "TIER_*" string (validated by Zod schema at runtime)
export interface EGOGiftData {
  tag: string[]
  keyword: string | null
  attributeType: string
  price: number
}

// i18n data from static/i18n/{lang}/egoGift/{id}.json
export interface EGOGiftI18n {
  name: string
  descs: string[]
  obtain: string
}

/**
 * EGO Gift list item for list/grid views.
 * Name is optional - populated when i18n is loaded, but components should prefer
 * EGOGiftName component for granular Suspense boundaries.
 */
export interface EGOGiftListItem {
  id: string
  /** Optional - populated when i18n is loaded */
  name?: string
  tag: string[]
  keyword: string | null
  attributeType: string
  themePack: string[]
  hardOnly?: boolean
  extremeOnly?: boolean
  recipe?: EGOGiftRecipe
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
  hardOnly?: boolean
  extremeOnly?: boolean
  recipe?: EGOGiftRecipe
}

export type EGOGiftSpecList = Record<string, EGOGiftSpec>
export type EGOGiftNameList = Record<string, string>
