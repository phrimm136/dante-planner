import { z } from 'zod'

/**
 * Start Buff Schemas
 *
 * Zod schemas for runtime validation of Mirror Dungeon start buff data
 * (static/data/MD{version}/startBuffs.json and static/i18n/{lang}/MD{version}/startBuffs.json).
 * Types are derived via z.infer — schemas are the single source of truth.
 */

/**
 * Reference data for buff effects with buff keywords
 */
export const BuffReferenceDataSchema = z.object({
  activeRound: z.number().optional(),
  buffKeyword: z.string().optional(),
  stack: z.number().optional(),
  turn: z.number().optional(),
  limit: z.number().optional(),
}).strict()

/**
 * Individual buff effect
 */
export const BuffEffectSchema = z.object({
  type: z.string(),
  value: z.number().optional(),
  value2: z.number().optional(),
  isTypoExist: z.boolean(),
  customLocalizeTextId: z.string().optional(),
  referenceData: BuffReferenceDataSchema.optional(),
}).strict()

/**
 * UI configuration for buff display
 */
export const BuffUIConfigSchema = z.object({
  iconSpriteId: z.string(),
}).strict()

/**
 * Start Buff entry from startBuffs.json
 */
export const StartBuffDataSchema = z.object({
  level: z.number(),
  baseId: z.number(),
  cost: z.number(),
  localizeId: z.string(),
  effects: z.array(BuffEffectSchema),
  uiConfig: BuffUIConfigSchema,
}).strict()

/**
 * Record of Start Buff data by ID
 */
export const StartBuffDataListSchema = z.record(z.string(), StartBuffDataSchema)

/**
 * Start Buff i18n translations (localizeId -> text)
 */
export const StartBuffI18nSchema = z.record(z.string(), z.string())

// ============================================================================
// Inferred Types
// ============================================================================

export type BuffReferenceData = z.infer<typeof BuffReferenceDataSchema>
export type BuffEffect = z.infer<typeof BuffEffectSchema>
export type BuffUIConfig = z.infer<typeof BuffUIConfigSchema>
export type StartBuffData = z.infer<typeof StartBuffDataSchema>
export type StartBuffDataList = z.infer<typeof StartBuffDataListSchema>
export type StartBuffI18n = z.infer<typeof StartBuffI18nSchema>
