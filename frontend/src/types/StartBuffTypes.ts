/**
 * Start Buff types for Mirror Dungeon 6
 */

/**
 * Enhancement level type (0 = base, 1 = +, 2 = ++)
 */
export type EnhancementLevel = 0 | 1 | 2

/**
 * Reference data for buff effects with buff keywords
 */
export interface BuffReferenceData {
  activeRound?: number
  buffKeyword?: string
  stack?: number
  turn?: number
  limit?: number
}

/**
 * Individual buff effect
 */
export interface BuffEffect {
  type: string
  value?: number
  value2?: number
  isTypoExist: boolean
  customLocalizeTextId?: string
  referenceData?: BuffReferenceData
}

/**
 * UI configuration for buff display
 */
export interface BuffUIConfig {
  iconSpriteId: string
}

/**
 * Start Buff data from startBuffsMD6.json
 */
export interface StartBuffData {
  level: number
  baseId: number
  cost: number
  localizeId: string
  effects: BuffEffect[]
  uiConfig: BuffUIConfig
}

/**
 * Record of Start Buff data by ID
 */
export type StartBuffDataList = Record<string, StartBuffData>

/**
 * Start Buff i18n translations
 */
export type StartBuffI18n = Record<string, string>

/**
 * Battle keyword entry from battleKeywords.json
 */
export interface BattleKeywordEntry {
  name: string
  desc: string
  iconId: string | null
  buffType: string
}

/**
 * Battle keywords dictionary
 */
export type BattleKeywords = Record<string, BattleKeywordEntry>

/**
 * Combined Start Buff for UI consumption
 */
export interface StartBuff {
  id: string
  baseId: number
  level: number
  name: string
  cost: number
  effects: BuffEffect[]
  iconSpriteId: string
}

/**
 * Base buff IDs (100-109)
 */
export const BASE_BUFF_IDS = [100, 101, 102, 103, 104, 105, 106, 107, 108, 109] as const

/**
 * Extracts base ID from full buff ID
 * @param id - Full buff ID (e.g., 100, 201, 302)
 * @returns Base ID (100-109)
 */
export function getBaseIdFromBuffId(id: number): number {
  return (id % 100) + 100
}

/**
 * Extracts enhancement level from full buff ID
 * @param id - Full buff ID (e.g., 100, 201, 302)
 * @returns Enhancement level (0, 1, or 2)
 */
export function getEnhancementFromBuffId(id: number): EnhancementLevel {
  const level = Math.floor(id / 100) - 1
  return level as EnhancementLevel
}

/**
 * Creates full buff ID from base ID and enhancement level
 * @param baseId - Base ID (100-109)
 * @param enhancement - Enhancement level (0, 1, or 2)
 * @returns Full buff ID
 */
export function createBuffId(baseId: number, enhancement: EnhancementLevel): number {
  const baseDigit = baseId % 100
  return (enhancement + 1) * 100 + baseDigit
}

/**
 * Gets enhancement suffix for display
 * @param enhancement - Enhancement level
 * @returns Suffix string (empty, "+", or "++")
 */
export function getEnhancementSuffix(enhancement: EnhancementLevel): string {
  if (enhancement === 1) return '+'
  if (enhancement === 2) return '++'
  return ''
}
