import { z } from 'zod'
import { DUNGEON_IDX } from '@/shared/gameData'
import { AbEventIdSchema, EGOGiftIdSchema } from '@/shared/gameData'

const dungeonIdxSchema = z.union([
  z.literal(DUNGEON_IDX.NORMAL),
  z.literal(DUNGEON_IDX.HARD),
  z.literal(DUNGEON_IDX.PARALLEL),
  z.literal(DUNGEON_IDX.EXTREME),
])

export const ExceptionConditionSchema = z
  .object({
    dungeonIdx: dungeonIdxSchema,
    selectableFloors: z.array(z.number()).optional(),
  })
  .strict()

// Only textColor is needed for runtime text overlay (hex color without # prefix)
export const ThemePackConfigSchema = z
  .object({
    textColor: z.string(),
  })
  .strict()

export const ThemePackI18nEntrySchema = z
  .object({
    name: z.string(),
    specialName: z.string().optional(),
  })
  .strict()

export const ThemePackI18nSchema = z.record(z.string(), ThemePackI18nEntrySchema)

export const ThemePackSpecSchema = z
  .object({
    exceptionConditions: z.array(ExceptionConditionSchema),
    specificEgoGiftPool: z.array(EGOGiftIdSchema),
    themePackConfig: ThemePackConfigSchema,
    fixedRewardEgoGifts: z.array(EGOGiftIdSchema).optional(),
  })
  .strict()

export const ThemePackListSchema = z.record(z.string(), ThemePackSpecSchema)

export const FeaturedBossSchema = z
  .object({
    unitId: z.string(),
    portraitId: z.string(),
  })
  .strict()

export type FeaturedBoss = z.infer<typeof FeaturedBossSchema>

const NodeOptionSchema = z.object({
  bossPool: z.array(z.string()),
  battlePool: z.array(z.string()),
  abBattlePool: z.array(z.string()),
  hardBattlePool: z.array(z.string()),
  hardAbBattlePool: z.array(z.string()),
  eventPool: z.array(AbEventIdSchema),
  specialEventPool: z.array(AbEventIdSchema).optional(),
})

export const ThemePackDetailSchema = z.object({
  exceptionConditions: z.array(ExceptionConditionSchema),
  nodeOption: NodeOptionSchema,
  egoGiftPool: z.array(EGOGiftIdSchema),
  specificEgoGiftPool: z.array(EGOGiftIdSchema),
  themePackConfig: ThemePackConfigSchema,
  featuredBosses: z.array(FeaturedBossSchema),
  hiddenThemeRate: z.number().optional(),
  fixedRewardEgoGifts: z.array(EGOGiftIdSchema).optional(),
})

export type ThemePackDetail = z.infer<typeof ThemePackDetailSchema>
