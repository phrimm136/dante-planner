import { z } from 'zod'

/**
 * Battle Keywords Schemas
 *
 * Zod schemas for runtime validation of battle keyword data structures.
 * These schemas mirror the TypeScript interfaces in types/StartBuffTypes.ts.
 *
 * MAINTENANCE: When TypeScript interfaces change, update these schemas
 * to maintain synchronization.
 */

/**
 * BattleKeywordEntry schema - individual keyword entry
 */
export const BattleKeywordEntrySchema = z.object({
  name: z.string(),
  desc: z.string(),
  iconId: z.string().nullable(),
  buffType: z.string(),
}).strict()

/**
 * BattleKeywords schema - Record of keyword entries
 */
export const BattleKeywordsSchema = z.record(
  z.string(),
  BattleKeywordEntrySchema
)
