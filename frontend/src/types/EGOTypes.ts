/**
 * EGO list data types
 */

export type EGORank = 'Zayin' | 'Teth' | 'He' | 'Waw' | 'Aleph'

export interface EGO {
  id: string
  name: string
  rank: EGORank
  sinner: string
  keywords: string[]
}

/**
 * EGO detail data types
 */

export interface EGOData {
  rank: EGORank
  // Additional EGO-specific fields will be added here
}

export interface EGOI18n {
  name: string
  character: string
  // Additional i18n fields will be added here
}
