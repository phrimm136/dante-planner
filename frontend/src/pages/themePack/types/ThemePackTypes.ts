import type { z } from 'zod'
import type { Entity } from '@/shared/filter'
import type { DungeonIdx, EncodedGiftId } from '@/shared/gameData'
import type { ThemePackSpecSchema, ThemePackListSchema } from '../schemas/ThemePackSchemas'
import type { ThemePackId } from '@/shared/gameData'

/** Theme pack entry from themePackList.json */
export type ThemePackSpec = z.infer<typeof ThemePackSpecSchema>

/** Theme pack entity: the spec entry plus its branded id */
export type ThemePackEntity = Entity<ThemePackId, ThemePackSpec>

/** Theme pack list - Record keyed by pack ID */
export type ThemePackList = z.infer<typeof ThemePackListSchema>

/** Floor-specific theme selection state for the planner (internal, not a data boundary) */
export interface FloorThemeSelection {
  themePackId: ThemePackId | null
  difficulty: DungeonIdx
  giftIds: Set<EncodedGiftId>
}

/**
 * Check if a theme pack is an extreme-only pack.
 * True only if ALL conditions are for extreme difficulty (dungeonIdx: 3);
 * mixed packs (available on normal/hard AND extreme) return false.
 */
export function isExtremePack(entry: ThemePackSpec): boolean {
  return (
    entry.exceptionConditions.length > 0 &&
    entry.exceptionConditions.every(
      (cond) => cond.dungeonIdx === 3 && cond.selectableFloors === undefined,
    )
  )
}
