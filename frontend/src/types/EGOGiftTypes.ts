// Raw data structure from egoGiftSpecList.json
// tag array must contain at least one "TIER_*" string
export interface EGOGiftSpec {
  tag: string[] & { __brand: 'HasTierTag' }
  keyword: string | null
  attributeType: string
}

// Raw data structure from static/data/egoGift/{id}.json
// tag array must contain at least one "TIER_*" string
export interface EGOGiftData {
  tag: string[] & { __brand: 'HasTierTag' }
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

// Merged EGO Gift for list view (spec + name from i18n)
// tag array must contain at least one "TIER_*" string
export interface EGOGiftListItem {
  id: string
  name: string
  tag: string[] & { __brand: 'HasTierTag' }
  keyword: string | null
  attributeType: string
}

export type EGOGiftSpecList = Record<string, EGOGiftSpec>
export type EGOGiftNameList = Record<string, string>
