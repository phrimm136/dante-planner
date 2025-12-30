import { z } from 'zod'

/**
 * Search Mapping Schemas
 *
 * Validation schemas for keyword and unit keyword mapping files used in search.
 * These files map internal game codes to user-friendly display names.
 *
 * Files:
 * - keywordMatch.json: Maps skill keywords (e.g., "Burst" -> "Rupture")
 * - unitKeywords.json: Maps unit trait codes (e.g., "BLADE_LINEAGE" -> "Blade Lineage")
 */

/**
 * Keyword match schema
 * Maps internal keyword codes to display names
 * Example: { "Burst": "Rupture", "Combustion": "Burn" }
 */
export const KeywordMatchSchema = z.record(z.string(), z.string())

/**
 * Unit keywords schema
 * Maps internal unit keyword codes to display names
 * Example: { "BLADE_LINEAGE": "Blade Lineage", "N_CORP": "N Corp." }
 */
export const UnitKeywordsSchema = z.record(z.string(), z.string())

// Type exports
export type KeywordMatch = z.infer<typeof KeywordMatchSchema>
export type UnitKeywords = z.infer<typeof UnitKeywordsSchema>
