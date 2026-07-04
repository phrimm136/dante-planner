import { z } from 'zod'

/**
 * Battle Keywords Schemas
 *
 * Zod schemas for runtime validation of the battle keyword i18n file
 * (static/i18n/{lang}/battleKeywords.json — name, desc, optional flavor).
 * Types are derived via z.infer — schemas are the single source of truth.
 */

/**
 * BattleKeywordEntry schema - individual keyword entry
 */
export const BattleKeywordEntrySchema = z
  .object({
    name: z.string(),
    desc: z.string(),
    flavor: z.string().optional(),
  })
  .strict()

/**
 * BattleKeywords schema - Record of keyword entries
 */
export const BattleKeywordsSchema = z.record(z.string(), BattleKeywordEntrySchema)

// ============================================================================
// Inferred Types
// ============================================================================

/**
 * Battle keyword i18n entry from battleKeywords.json
 * Used by schemas and data hooks for validating the trimmed i18n file.
 */
export type BattleKeywordI18nEntry = z.infer<typeof BattleKeywordEntrySchema>
