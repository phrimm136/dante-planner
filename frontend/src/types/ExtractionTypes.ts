/**
 * Extraction (Gacha) Types
 *
 * Type definitions for extraction probability calculator.
 * Used to track user inputs and calculation results.
 *
 * @see ExtractionSchemas.ts for Zod validation
 * @see lib/extractionCalculator.ts for probability math
 */

/**
 * Target types available for extraction
 * - threeStarId: 3-star Identity (rate-up)
 * - ego: EGO (rate-up)
 * - announcer: Announcer skin (rate-up, when featured)
 */
export type ExtractionTargetType = 'threeStarId' | 'ego' | 'announcer'

/**
 * Banner modifier flags that affect rates
 */
export interface BannerModifiers {
  /** User has collected all EGO from this banner */
  allEgoCollected: boolean
  /** Banner features an Announcer */
  hasAnnouncer: boolean
}

/**
 * Target configuration for a single extraction target
 * Represents a featured item the user wants to obtain
 */
export interface ExtractionTarget {
  /** Type of target (threeStarId, ego, announcer) */
  type: ExtractionTargetType
  /** Number of copies user wants (for multi-copy goals) */
  wantedCopies: number
  /** Number of copies already obtained */
  currentCopies: number
}

/**
 * User input configuration for extraction calculator
 * All fields needed to compute probability
 */
export interface ExtractionInput {
  /** Number of pulls the user plans to perform */
  plannedPulls: number
  /** Number of featured 3-star Identities on banner */
  featuredThreeStarCount: number
  /** Number of featured EGO on banner */
  featuredEgoCount: number
  /** Number of featured Announcers on banner */
  featuredAnnouncerCount: number
  /** Banner modifier flags */
  modifiers: BannerModifiers
  /** Specific targets user wants to calculate probability for */
  targets: ExtractionTarget[]
  /** Current pity counter (pulls since last 3-star) */
  currentPity: number
}

/**
 * Probability result for a single target
 */
export interface TargetProbability {
  /** The target this result is for */
  target: ExtractionTarget
  /** Probability of obtaining desired copies (0-1) */
  probability: number
  /** Expected number of pulls to reach goal */
  expectedPulls: number
  /** Whether pity contributes to this probability */
  pityApplies: boolean
}

/**
 * Complete calculation results
 */
export interface ExtractionResult {
  /** Probability results per target */
  targetResults: TargetProbability[]
  /** Probability of getting at least one of ANY target */
  anyTargetProbability: number
  /** Probability of getting ALL targets with optimal pity usage */
  allTargetProbability: number
  /** Number of pity triggers available (floor(totalPulls / 200)) */
  pityCount: number
  /** Total lunacy cost for planned pulls */
  lunacyCost: number
  /** Pulls until next pity trigger */
  pullsUntilPity: number
  /** Active rate table name for display */
  activeRateTable: 'standard' | 'withAnnouncer' | 'allEgoCollected' | 'allEgoWithAnnouncer'
}

/**
 * Effective rates after applying modifiers and splitting among featured items
 */
export interface EffectiveRates {
  /** Effective rate for each featured 3-star ID */
  threeStarIdEach: number
  /** Effective rate for each featured EGO */
  egoEach: number
  /** Effective rate for Announcer (if featured) */
  announcer: number
  /** Total 3-star ID rate (base + rate-up) */
  threeStarIdTotal: number
  /** Total EGO rate (base + rate-up) */
  egoTotal: number
}
