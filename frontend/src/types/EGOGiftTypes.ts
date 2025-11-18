/**
 * EGO Gift specification data from EGOGiftSpecList.json
 */
export interface EGOGiftSpec {
  keywords: string[]
  themePack: string[]
  cost: number
  tier: string
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
 * Combined EGO Gift data for UI consumption
 */
export interface EGOGift {
  id: string
  name: string
  keywords: string[]
  themePack: string[]
  cost: number
  tier: string
}

/**
 * Record of EGO Gift specs by ID
 */
export type EGOGiftSpecList = Record<string, EGOGiftSpec>

/**
 * Record of EGO Gift names by ID
 */
export type EGOGiftNameList = Record<string, string>
