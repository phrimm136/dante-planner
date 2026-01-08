import { z } from 'zod'
import { DUNGEON_IDX } from '@/lib/constants'

/**
 * Theme Pack Schemas
 *
 * Zod schemas for runtime validation of theme pack data structures.
 * These schemas mirror the TypeScript interfaces in types/ThemePackTypes.ts
 * and provide strict runtime validation.
 */

// Dungeon index schema - 0, 1, or 3 (no 2)
const dungeonIdxSchema = z.union([
  z.literal(DUNGEON_IDX.NORMAL),
  z.literal(DUNGEON_IDX.HARD),
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
}).strict()

// Theme pack list schema (Record keyed by pack ID)
export const ThemePackListSchema = z.record(z.string(), ThemePackEntrySchema)
