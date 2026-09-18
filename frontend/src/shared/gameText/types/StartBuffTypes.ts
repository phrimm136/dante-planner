import type { BuffEffect } from '../schemas/StartBuffSchemas'

export type {
  BuffReferenceData,
  BuffEffect,
  BuffUIConfig,
  StartBuffData,
  StartBuffDataList,
  StartBuffI18n,
} from '../schemas/StartBuffSchemas'

export type { BattleKeywordI18nEntry } from '../schemas/BattleKeywordsSchemas'

export type EnhancementLevel = 0 | 1 | 2

export interface BattleKeywordEntry {
  name: string
  desc: string
  flavor?: string
  iconId: string | null
  buffType: string
}

export type BattleKeywords = Record<string, BattleKeywordEntry>

export interface StartBuff {
  id: string
  baseId: number
  level: number
  name: string
  cost: number
  effects: BuffEffect[]
  iconSpriteId: string
}

export const BASE_BUFF_IDS = [100, 101, 102, 103, 104, 105, 106, 107, 108, 109] as const

export function getBaseIdFromBuffId(id: number): number {
  return (id % 100) + 100
}

export function getEnhancementFromBuffId(id: number): EnhancementLevel {
  const level = Math.floor(id / 100) - 1
  return level as EnhancementLevel
}

export function createBuffId(baseId: number, enhancement: EnhancementLevel): number {
  const baseDigit = baseId % 100
  return (enhancement + 1) * 100 + baseDigit
}

export function getEnhancementSuffix(enhancement: EnhancementLevel): string {
  if (enhancement === 1) return '+'
  if (enhancement === 2) return '++'
  return ''
}

export function deriveEnhancements(selectedBuffIds: Set<number>): Record<number, EnhancementLevel> {
  const result: Record<number, EnhancementLevel> = {}
  for (const buffId of selectedBuffIds) {
    const baseId = getBaseIdFromBuffId(buffId)
    const enhancement = getEnhancementFromBuffId(buffId)
    result[baseId] = enhancement
  }
  return result
}
