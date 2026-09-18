import type { z } from 'zod'
import type { Entity } from '@/shared/filter'
import type { DungeonIdx, EncodedGiftId } from '@/shared/gameData'
import type { ThemePackSpecSchema, ThemePackListSchema } from '../schemas/ThemePackSchemas'
import type { ThemePackId } from '@/shared/gameData'

export type ThemePackSpec = z.infer<typeof ThemePackSpecSchema>

export type ThemePackEntity = Entity<ThemePackId, ThemePackSpec>

export type ThemePackList = z.infer<typeof ThemePackListSchema>

export interface FloorThemeSelection {
  themePackId: ThemePackId | null
  difficulty: DungeonIdx
  giftIds: Set<EncodedGiftId>
}

export function isExtremePack(entry: ThemePackSpec): boolean {
  return (
    entry.exceptionConditions.length > 0 &&
    entry.exceptionConditions.every(
      (cond) => cond.dungeonIdx === 3 && cond.selectableFloors === undefined,
    )
  )
}
