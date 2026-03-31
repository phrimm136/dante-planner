import { z } from 'zod'

/**
 * Keyword Schemas
 *
 * Zod schemas for runtime validation of battle keyword spec data structures.
 * These schemas validate the spec file (battleKeywordSpecList.json) which
 * contains language-independent keyword metadata and entity backlinks.
 *
 * MAINTENANCE: When TypeScript interfaces change, update these schemas
 * to maintain synchronization.
 */

/**
 * BattleKeywordSpecEntry schema - individual keyword spec entry
 */
export const BattleKeywordSpecEntrySchema = z.object({
  iconId: z.string().nullable(),
  buffType: z.string(),
  identities: z.array(z.string()),
  egos: z.array(z.string()),
  egoGifts: z.array(z.string()),
}).strict()

/**
 * BattleKeywordSpecList schema - Record of keyword spec entries
 */
export const BattleKeywordSpecListSchema = z.record(
  z.string(),
  BattleKeywordSpecEntrySchema
)

/**
 * BattleKeywordNameList schema - Record of keyword names for lookup
 */
export const BattleKeywordNameListSchema = z.record(z.string(), z.string())
