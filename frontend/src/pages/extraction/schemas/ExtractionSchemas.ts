import { z } from 'zod'
import { EXTRACTION_RATES } from '../lib/extractionRates'

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
  /** Number of featured 3-star Identities (0+; 0 on an EGO-only banner) */
  featuredThreeStarCount: z.number().int().min(0).max(20),
  /** Number of featured EGO (0+, can be 0 if all collected) */
  featuredEgoCount: z.number().int().min(0).max(20),
  /** Number of featured Announcers (0+; most banners have none) */
  featuredAnnouncerCount: z.number().int().min(0).max(20),
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
).refine(
  (data) => {
    // 3-star target requires featuredThreeStarCount > 0 (EGO-only banners feature 0)
    const hasThreeStarTarget = data.targets.some((t) => t.type === 'threeStarId')
    if (hasThreeStarTarget && data.featuredThreeStarCount === 0) {
      return false
    }
    return true
  },
  { message: 'Cannot have a 3-star target when featuredThreeStarCount is 0' }
).refine(
  (data) => {
    // Announcer target requires featuredAnnouncerCount > 0 (guards the rate division)
    const hasAnnouncerTarget = data.targets.some((t) => t.type === 'announcer')
    if (hasAnnouncerTarget && data.featuredAnnouncerCount === 0) {
      return false
    }
    return true
  },
  { message: 'Cannot have an Announcer target when featuredAnnouncerCount is 0' }
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

export const SuccessiveProbabilitySchema = z.object({
  /** Number of items */
  count: z.number().int().min(0),
  /** Probability of obtaining at least this many items */
  probability: z.number().min(0).max(1),
}).strict()

/**
 * Complete extraction result schema
 */
export const ExtractionResultSchema = z.object({
  /** Results per target */
  targetResults: z.array(TargetProbabilitySchema),
  /** Any target probability (0-1) */
  anyTargetProbability: z.number().min(0).max(1),
  /** All target probability (0-1) */
  allTargetProbability: z.number().min(0).max(1),
  /** Successive probabilities: P(n), P(n-1), ..., P(1) with pity applied */
  successiveProbabilities: z.array(SuccessiveProbabilitySchema),
  /** Total items wanted (sum of all target wanted counts) */
  totalItemsWanted: z.number().int().min(0),
  /** Number of pity triggers available (floor(totalPulls / 200)) */
  pityCount: z.number().int().min(0),
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
