import type { DungeonIdx } from '@/lib/constants'

/**
 * Exception condition for theme pack floor/difficulty availability
 * From themePackList.json exceptionConditions array
 */
export interface ExceptionCondition {
  dungeonIdx: DungeonIdx
  selectableFloors?: number[] // 0-4 mapping, undefined for extreme (all 11-15F)
}

/**
 * Theme pack visual configuration (simplified for pre-composed images)
 * Only textColor is needed for runtime text overlay (hex color without # prefix)
 */
export interface ThemePackConfig {
  textColor: string,
}

/**
 * Theme pack entry from themePackList.json
 */
export interface ThemePackEntry {
  exceptionConditions: ExceptionCondition[]
  specificEgoGiftPool: number[]
  themePackConfig: ThemePackConfig
}

/**
 * Theme pack list - Record keyed by pack ID
 */
export type ThemePackList = Record<string, ThemePackEntry>

/**
 * Floor-specific theme selection state for planner
 */
export interface FloorThemeSelection {
  themePackId: string | null
  difficulty: DungeonIdx
  giftIds: Set<string>
}

/**
 * Check if a theme pack is an extreme-only pack
 * Returns true only if ALL conditions are for extreme difficulty (dungeonIdx: 3)
 * Mixed packs (available on both normal/hard AND extreme) return false
 */
export function isExtremePack(entry: ThemePackEntry): boolean {
  return entry.exceptionConditions.every(
    (cond) => cond.dungeonIdx === 3 && cond.selectableFloors === undefined
  )
}
