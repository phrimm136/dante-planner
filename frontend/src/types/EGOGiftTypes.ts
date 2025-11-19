/**
 * EGO Gift specification data from EGOGiftSpecList.json
 */
export interface EGOGiftSpec {
  category: string
  keywords: string[] // Used for search functionality (e.g., "Haste")
  themePack: string[]
  tier: string
}

/**
 * EGO Gift detail data from egoGift/{id}.json
 */
export interface EGOGiftData {
  category: string
  tier: string
  cost: number
}

/**
 * EGO Gift i18n data from gift/{id}.json
 */
export interface EGOGiftI18n {
  name: string
  descs: string[]
  obtain: string
}

/**
 * Combined EGO Gift data for UI consumption (list page)
 */
export interface EGOGift {
  id: string
  name: string
  category: string
  keywords: string[] // Used for search functionality (e.g., "Haste")
  themePack: string[]
  tier: string
  enhancement: number
}

/**
 * Record of EGO Gift specs by ID
 */
export type EGOGiftSpecList = Record<string, EGOGiftSpec>

/**
 * Record of EGO Gift names by ID
 */
export type EGOGiftNameList = Record<string, string>
