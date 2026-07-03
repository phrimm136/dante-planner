import type { z } from 'zod'
import type { DungeonIdx } from '@/shared/gameData'
import type {
  ExceptionConditionSchema,
  ThemePackConfigSchema,
  ThemePackEntrySchema,
  ThemePackListSchema,
} from '../schemas/ThemePackSchemas'

/** Exception condition for theme pack floor/difficulty availability (themePackList.json) */
export type ExceptionCondition = z.infer<typeof ExceptionConditionSchema>

/** Theme pack visual configuration; textColor is the hex (no #) for runtime text overlay */
export type ThemePackConfig = z.infer<typeof ThemePackConfigSchema>

/** Theme pack entry from themePackList.json */
export type ThemePackEntry = z.infer<typeof ThemePackEntrySchema>

/** Theme pack list - Record keyed by pack ID */
export type ThemePackList = z.infer<typeof ThemePackListSchema>

/** Floor-specific theme selection state for the planner (internal, not a data boundary) */
export interface FloorThemeSelection {
  themePackId: string | null
  difficulty: DungeonIdx
  giftIds: Set<string>
}

/**
 * Check if a theme pack is an extreme-only pack.
 * True only if ALL conditions are for extreme difficulty (dungeonIdx: 3);
 * mixed packs (available on normal/hard AND extreme) return false.
 */
export function isExtremePack(entry: ThemePackEntry): boolean {
  return entry.exceptionConditions.length > 0 && entry.exceptionConditions.every(
    (cond) => cond.dungeonIdx === 3 && cond.selectableFloors === undefined
  )
}
