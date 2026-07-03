import { z } from 'zod'
import { DUNGEON_IDX } from '@/shared/gameData'

/**
 * Theme Pack Schemas
 *
 * Zod schemas for runtime validation of theme pack data structures.
 * These schemas mirror the TypeScript interfaces in types/ThemePackTypes.ts
 * and provide strict runtime validation.
 */

// Dungeon index schema - 0, 1, 2, or 3
const dungeonIdxSchema = z.union([
  z.literal(DUNGEON_IDX.NORMAL),
  z.literal(DUNGEON_IDX.HARD),
  z.literal(DUNGEON_IDX.PARALLEL),
  z.literal(DUNGEON_IDX.EXTREME),
])

// Exception condition schema
export const ExceptionConditionSchema = z.object({
  dungeonIdx: dungeonIdxSchema,
  selectableFloors: z.array(z.number()).optional(),
}).strict()

// Theme pack config schema - simplified for pre-composed images
// Only textColor is needed for runtime text overlay (hex color without # prefix)
export const ThemePackConfigSchema = z.object({
  textColor: z.string(),
}).strict()

// Theme pack i18n entry schema
export const ThemePackI18nEntrySchema = z.object({
  name: z.string(),
  specialName: z.string().optional(),
}).strict()

// Theme pack i18n record schema
export const ThemePackI18nSchema = z.record(z.string(), ThemePackI18nEntrySchema)

// Theme pack entry schema
export const ThemePackEntrySchema = z.object({
  exceptionConditions: z.array(ExceptionConditionSchema),
  specificEgoGiftPool: z.array(z.number()),
  themePackConfig: ThemePackConfigSchema,
  fixedRewardEgoGifts: z.array(z.number()).optional(),
}).strict()

// Theme pack list schema (Record keyed by pack ID)
export const ThemePackListSchema = z.record(z.string(), ThemePackEntrySchema)

// Featured boss schema (unitId + portrait reference for a theme pack's boss roster)
// portraitId is a string when the source field is a string sdPortrait, number otherwise
export const FeaturedBossSchema = z.object({
  unitId: z.number(),
  portraitId: z.union([z.number(), z.string()]),
}).strict()

export type FeaturedBoss = z.infer<typeof FeaturedBossSchema>

// Node option schema (battle/event pools in individual theme pack files)
const NodeOptionSchema = z.object({
  bossPool: z.array(z.number()),
  battlePool: z.array(z.number()),
  abBattlePool: z.array(z.number()),
  hardBattlePool: z.array(z.number()),
  hardAbBattlePool: z.array(z.number()),
  eventPool: z.array(z.number()),
  specialEventPool: z.array(z.number()).optional(),
})

// Individual theme pack detail schema (full data from themePack/{id}.json)
export const ThemePackDetailSchema = z.object({
  exceptionConditions: z.array(ExceptionConditionSchema),
  nodeOption: NodeOptionSchema,
  egoGiftPool: z.array(z.number()),
  specificEgoGiftPool: z.array(z.number()),
  themePackConfig: ThemePackConfigSchema,
  featuredBosses: z.array(FeaturedBossSchema),
  hiddenThemeRate: z.number().optional(),
  fixedRewardEgoGifts: z.array(z.number()).optional(),
})

export type ThemePackDetail = z.infer<typeof ThemePackDetailSchema>
