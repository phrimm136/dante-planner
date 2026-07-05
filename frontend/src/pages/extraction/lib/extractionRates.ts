/**
 * Extraction (Gacha) Rate Constants
 * Used by extraction probability calculator
 *
 * Rate mechanics:
 * - Standard rates apply when banner has EGO available
 * - "All EGO Collected" shifts EGO rate to ID pool
 * - Announcer rate takes from 1★ ID pool when featured
 * - Rate-up is split evenly among featured items of same type
 *
 * @see ExtractionPlannerPage for usage
 */
export const EXTRACTION_RATES = {
  /**
   * Base rates for standard banner (no modifiers)
   * Total: 2.9% 3★ID + 12.8% 2★ID + 83.0% 1★ID + 1.3% EGO = 100%
   *
   * EGO breakdown:
   * - Rate-up EGO: 0.65% (split among featured)
   * - Non-rate-up (pik-tteul): 0.65%
   */
  STANDARD: {
    THREE_STAR_ID: 0.029,
    TWO_STAR_ID: 0.128,
    ONE_STAR_ID: 0.83,
    EGO: 0.013,
    ANNOUNCER: 0,
  },
  /** Rates when Announcer is featured (takes from 1★ pool) */
  WITH_ANNOUNCER: {
    THREE_STAR_ID: 0.029,
    TWO_STAR_ID: 0.128,
    ONE_STAR_ID: 0.817,
    EGO: 0.013,
    ANNOUNCER: 0.013,
  },
  /**
   * Rates when "All EGO Collected" (owned all non-featured EGO)
   *
   * When user owns all non-rate-up EGO:
   * - Non-rate-up (pik-tteul) EGO 0.65% becomes impossible
   * - Rate-up EGO gets FULL 1.3% (doubled from 0.65%)
   * - ID rates also increase slightly (3★: 2.9% → 3.0%)
   * - 1★ absorbs remaining probability to maintain 100% total
   */
  ALL_EGO_COLLECTED: {
    THREE_STAR_ID: 0.03,
    TWO_STAR_ID: 0.13,
    ONE_STAR_ID: 0.84,
    EGO: 0.0, // No pik-tteul possible; rate-up handled separately
    ANNOUNCER: 0,
  },
  /** Rates with both All EGO + Announcer */
  ALL_EGO_WITH_ANNOUNCER: {
    THREE_STAR_ID: 0.03,
    TWO_STAR_ID: 0.13,
    ONE_STAR_ID: 0.827,
    EGO: 0.0, // No pik-tteul possible; rate-up handled separately
    ANNOUNCER: 0.013,
  },
  /**
   * Rate-up totals (split among featured items)
   *
   * For EGO:
   * - Standard: 0.65% (half of 1.3% EGO pool)
   * - All EGO Collected: 1.3% (full EGO pool, no pik-tteul)
   */
  RATE_UP: {
    THREE_STAR_ID: 0.0145,
    /** Standard rate-up EGO (when pik-tteul exists) */
    EGO: 0.0065,
    /** Rate-up EGO when all EGO collected (no pik-tteul, full pool) */
    EGO_ALL_COLLECTED: 0.013,
    ANNOUNCER: 0.013,
  },
  /** Pity system - guaranteed at this pull count */
  PITY_PULLS: 200,
  /** Lunacy cost per single pull */
  LUNACY_PER_PULL: 130,
} as const

/**
 * Extraction rate table type - base rates for each item type
 * Values are 0-1 probabilities
 */
export interface ExtractionRateTable {
  THREE_STAR_ID: number
  TWO_STAR_ID: number
  ONE_STAR_ID: number
  EGO: number
  ANNOUNCER: number
}
