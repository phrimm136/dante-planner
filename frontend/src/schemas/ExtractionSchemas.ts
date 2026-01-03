import { z } from 'zod'
import { EXTRACTION_RATES } from '@/lib/constants'

/**
 * Extraction Schemas
 *
 * Zod schemas for runtime validation of extraction calculator inputs.
 * These schemas mirror the TypeScript interfaces in types/ExtractionTypes.ts
 * and provide strict runtime validation for user inputs.
 *
 * @see types/ExtractionTypes.ts for type definitions
 * @see lib/extractionCalculator.ts for usage
 */

// ============================================================================
// Enum Schemas
// ============================================================================

/**
 * Extraction target type schema
 * Validates target type: threeStarId, ego, or announcer
 */
export const ExtractionTargetTypeSchema = z.enum(['threeStarId', 'ego', 'announcer'])

/**
 * Active rate table schema
 * Identifies which rate table is in use
 */
export const ActiveRateTableSchema = z.enum([
  'standard',
  'withAnnouncer',
  'allEgoCollected',
  'allEgoWithAnnouncer',
])

// ============================================================================
// Component Schemas
// ============================================================================

/**
 * Banner modifiers schema
 * Validates modifier flags that affect extraction rates
 */
export const BannerModifiersSchema = z.object({
  /** User has collected all EGO from this banner */
  allEgoCollected: z.boolean(),
  /** Banner features an Announcer */
  hasAnnouncer: z.boolean(),
}).strict()

/**
 * Extraction target schema
 * Validates a single target configuration
 */
export const ExtractionTargetSchema = z.object({
  /** Type of target */
  type: ExtractionTargetTypeSchema,
  /** Number of copies user wants (1+) */
  wantedCopies: z.number().int().min(1),
  /** Number of copies already obtained (0+) */
  currentCopies: z.number().int().min(0),
}).strict().refine(
  (data) => data.currentCopies < data.wantedCopies,
  { message: 'currentCopies must be less than wantedCopies' }
)

/**
 * Extraction input schema
 * Validates complete user input for probability calculation
 */
export const ExtractionInputSchema = z.object({
  /** Number of pulls (0 to reasonable max) */
  plannedPulls: z.number().int().min(0).max(10000),
  /** Number of featured 3-star Identities (1+) */
  featuredThreeStarCount: z.number().int().min(1).max(20),
  /** Number of featured EGO (0+, can be 0 if all collected) */
  featuredEgoCount: z.number().int().min(0).max(20),
  /** Banner modifiers */
  modifiers: BannerModifiersSchema,
  /** Target configurations */
  targets: z.array(ExtractionTargetSchema),
  /** Current pity counter (0 to PITY_PULLS-1) */
  currentPity: z.number().int().min(0).max(EXTRACTION_RATES.PITY_PULLS - 1),
}).strict().refine(
  (data) => {
    // If allEgoCollected, featuredEgoCount should be 0
    if (data.modifiers.allEgoCollected && data.featuredEgoCount > 0) {
      return false
    }
    return true
  },
  { message: 'featuredEgoCount must be 0 when allEgoCollected is true' }
).refine(
  (data) => {
    // All EGO targets require featuredEgoCount > 0
    const hasEgoTarget = data.targets.some((t) => t.type === 'ego')
    if (hasEgoTarget && data.featuredEgoCount === 0) {
      return false
    }
    return true
  },
  { message: 'Cannot have EGO target when featuredEgoCount is 0' }
).refine(
  (data) => {
    // Announcer target requires hasAnnouncer modifier
    const hasAnnouncerTarget = data.targets.some((t) => t.type === 'announcer')
    if (hasAnnouncerTarget && !data.modifiers.hasAnnouncer) {
      return false
    }
    return true
  },
  { message: 'Cannot have Announcer target when banner has no Announcer' }
)

// ============================================================================
// Result Schemas (for type safety, not typically validated at runtime)
// ============================================================================

/**
 * Target probability result schema
 */
export const TargetProbabilitySchema = z.object({
  /** The target this result is for */
  target: ExtractionTargetSchema,
  /** Probability (0-1) */
  probability: z.number().min(0).max(1),
  /** Expected pulls */
  expectedPulls: z.number().min(0),
  /** Whether pity applies */
  pityApplies: z.boolean(),
}).strict()

/**
 * Complete extraction result schema
 */
export const ExtractionResultSchema = z.object({
  /** Results per target */
  targetResults: z.array(TargetProbabilitySchema),
  /** Any target probability (0-1) */
  anyTargetProbability: z.number().min(0).max(1),
  /** Total lunacy cost */
  lunacyCost: z.number().int().min(0),
  /** Pulls until pity */
  pullsUntilPity: z.number().int().min(0).max(EXTRACTION_RATES.PITY_PULLS),
  /** Active rate table */
  activeRateTable: ActiveRateTableSchema,
}).strict()

/**
 * Effective rates schema
 */
export const EffectiveRatesSchema = z.object({
  /** Rate per featured 3-star ID */
  threeStarIdEach: z.number().min(0).max(1),
  /** Rate per featured EGO */
  egoEach: z.number().min(0).max(1),
  /** Announcer rate */
  announcer: z.number().min(0).max(1),
  /** Total 3-star ID rate */
  threeStarIdTotal: z.number().min(0).max(1),
  /** Total EGO rate */
  egoTotal: z.number().min(0).max(1),
}).strict()

// ============================================================================
// Type Exports (inferred from schemas)
// ============================================================================

/** Inferred type for ExtractionTargetType */
export type ExtractionTargetTypeInferred = z.infer<typeof ExtractionTargetTypeSchema>

/** Inferred type for BannerModifiers */
export type BannerModifiersInferred = z.infer<typeof BannerModifiersSchema>

/** Inferred type for ExtractionTarget */
export type ExtractionTargetInferred = z.infer<typeof ExtractionTargetSchema>

/** Inferred type for ExtractionInput */
export type ExtractionInputInferred = z.infer<typeof ExtractionInputSchema>

/** Inferred type for ExtractionResult */
export type ExtractionResultInferred = z.infer<typeof ExtractionResultSchema>

/** Inferred type for EffectiveRates */
export type EffectiveRatesInferred = z.infer<typeof EffectiveRatesSchema>
