// Raw data structure from egoGiftSpecList.json
export interface EGOGiftSpec {
  tag: string[]
  keyword: string | null
  attributeType: string
}

// Raw data structure from static/data/egoGift/{id}.json
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

// Merged EGO Gift for list view (spec + name from i18n)
export interface EGOGiftListItem {
  id: string
  name: string
  tag: string[]
  keyword: string | null
  attributeType: string
}

export type EGOGiftSpecList = Record<string, EGOGiftSpec>
export type EGOGiftNameList = Record<string, string>
