/**
 * Extraction (Gacha) Probability Calculator
 *
 * Pure functions for calculating extraction probabilities.
 * All functions have no side effects and depend only on their inputs.
 *
 * ## Extraction Mechanics
 *
 * - **EGO**: 비복원추출 (without replacement) - once obtained, can't get duplicates
 *   - Every EGO hit is guaranteed to be a NEW EGO
 *   - "Want 4 EGOs" = "Need at least 4 EGO hits" (simple binomial)
 *
 * - **Identity/Announcer**: 복원추출 (with replacement) - can get duplicates
 *   - "Want 2 different IDs" = "Need to hit each specific ID at least once"
 *   - This is the Coupon Collector problem
 *
 * @see ExtractionTypes.ts for type definitions
 * @see constants.ts for EXTRACTION_RATES
 */

import { EXTRACTION_RATES, type ExtractionRateTable } from '@/lib/constants'
import type {
  BannerModifiers,
  EffectiveRates,
  ExtractionInput,
  ExtractionResult,
  ExtractionTarget,
  ExtractionTargetType,
  TargetProbability,
} from '@/types/ExtractionTypes'

/**
 * Get the appropriate rate table based on banner modifiers
 *
 * @param allEgoCollected - User has collected all EGO from banner
 * @param hasAnnouncer - Banner features an Announcer
 * @returns The rate table to use for calculations
 */
export function getEffectiveRates(
  allEgoCollected: boolean,
  hasAnnouncer: boolean
): ExtractionRateTable {
  if (allEgoCollected && hasAnnouncer) {
    return EXTRACTION_RATES.ALL_EGO_WITH_ANNOUNCER
  }
  if (allEgoCollected) {
    return EXTRACTION_RATES.ALL_EGO_COLLECTED
  }
  if (hasAnnouncer) {
    return EXTRACTION_RATES.WITH_ANNOUNCER
  }
  return EXTRACTION_RATES.STANDARD
}

/**
 * Get the rate table name for display purposes
 *
 * @param allEgoCollected - User has collected all EGO from banner
 * @param hasAnnouncer - Banner features an Announcer
 * @returns The rate table name
 */
export function getRateTableName(
  allEgoCollected: boolean,
  hasAnnouncer: boolean
): ExtractionResult['activeRateTable'] {
  if (allEgoCollected && hasAnnouncer) {
    return 'allEgoWithAnnouncer'
  }
  if (allEgoCollected) {
    return 'allEgoCollected'
  }
  if (hasAnnouncer) {
    return 'withAnnouncer'
  }
  return 'standard'
}

/**
 * Calculate the per-item rate for a specific target type
 *
 * Rate-up totals are split evenly among featured items of the same category.
 *
 * @param type - Target type (threeStarId, ego, announcer)
 * @param featuredCount - Number of featured items in this category
 * @param allEgoCollected - Whether user owns all non-rate-up EGO (doubles EGO rate-up)
 * @returns Rate per individual item (0-1)
 */
export function calculateRateForTarget(
  type: ExtractionTargetType,
  featuredCount: number,
  allEgoCollected: boolean = false
): number {
  if (featuredCount <= 0) {
    return 0
  }

  switch (type) {
    case 'threeStarId':
      return EXTRACTION_RATES.RATE_UP.THREE_STAR_ID / featuredCount
    case 'ego':
      // When all EGO collected: rate-up gets full 1.3% instead of 0.65%
      const egoRate = allEgoCollected
        ? EXTRACTION_RATES.RATE_UP.EGO_ALL_COLLECTED
        : EXTRACTION_RATES.RATE_UP.EGO
      return egoRate / featuredCount
    case 'announcer':
      return EXTRACTION_RATES.RATE_UP.ANNOUNCER / featuredCount
    default:
      return 0
  }
}

/**
 * Calculate probability of obtaining at least one copy in N pulls
 *
 * Uses binomial probability: P(X >= 1) = 1 - P(X = 0) = 1 - (1 - rate)^N
 *
 * @param pulls - Number of pulls
 * @param rate - Per-pull probability (0-1)
 * @returns Probability of at least one success (0-1)
 */
export function calculateSingleTargetProbability(pulls: number, rate: number): number {
  if (pulls <= 0 || rate <= 0) {
    return 0
  }
  if (rate >= 1) {
    return 1
  }

  // P(at least 1) = 1 - P(none) = 1 - (1-rate)^pulls
  const probability = 1 - Math.pow(1 - rate, pulls)

  // Clamp to [0, 1] to handle floating point edge cases
  return Math.min(1, Math.max(0, probability))
}

/**
 * Calculate probability of obtaining at least K hits in N pulls (binomial)
 *
 * Uses binomial CDF: P(X >= k) = 1 - P(X < k) = 1 - sum(P(X = i) for i in 0..k-1)
 *
 * Used for:
 * - EGO (비복원추출): "want 4 EGOs" = "need at least 4 EGO hits"
 *
 * @param pulls - Number of pulls
 * @param rate - Per-pull probability (0-1)
 * @param hitsNeeded - Minimum number of hits wanted
 * @returns Probability of getting at least K hits (0-1)
 */
export function calculateAtLeastKHits(
  pulls: number,
  rate: number,
  hitsNeeded: number
): number {
  if (hitsNeeded <= 0) {
    return 1 // Trivially satisfied: wanting 0 hits is always achieved
  }
  if (pulls <= 0 || rate <= 0) {
    return 0
  }
  if (rate >= 1) {
    return pulls >= hitsNeeded ? 1 : 0
  }
  if (pulls < hitsNeeded) {
    return 0 // Can't get more hits than pulls
  }

  // Calculate P(X < hitsNeeded) using binomial PMF
  let cumulativeLessThan = 0
  for (let k = 0; k < hitsNeeded; k++) {
    cumulativeLessThan += binomialPmf(pulls, k, rate)
  }

  // P(X >= hitsNeeded) = 1 - P(X < hitsNeeded)
  const probability = 1 - cumulativeLessThan

  return Math.min(1, Math.max(0, probability))
}

/**
 * Calculate probability of getting all N different items from M featured (Coupon Collector)
 *
 * Used for:
 * - Identity/Announcer (복원추출): "want 2 different IDs from 2 featured"
 *
 * Each specific item has rate = totalRate / featuredCount
 * P(get all N) = product of P(hit each specific item at least once)
 * P(hit specific item at least once) = 1 - (1 - itemRate)^pulls
 *
 * @param pulls - Number of pulls
 * @param totalRate - Total rate for this category (e.g., 1.45% for rate-up ID)
 * @param featuredCount - Total number of featured items in pool
 * @param wantedCount - Number of different items wanted
 * @returns Probability of getting wantedCount different items (0-1)
 */
export function calculateCouponCollectorProbability(
  pulls: number,
  totalRate: number,
  featuredCount: number,
  wantedCount: number
): number {
  if (wantedCount <= 0) {
    return 1 // Wanting 0 is always achieved
  }
  if (pulls <= 0 || totalRate <= 0 || featuredCount <= 0) {
    return 0
  }
  if (wantedCount > featuredCount) {
    return 0 // Can't get more different items than available
  }

  // Rate per specific item
  const itemRate = totalRate / featuredCount

  // P(hit specific item at least once) = 1 - (1 - itemRate)^pulls
  const probHitOne = 1 - Math.pow(1 - itemRate, pulls)

  // For "want N different from M", we need to hit any N specific items
  // Simplification: assume user wants ANY N of the M items (not specific ones)
  // This is P(hit at least N different items from M)
  //
  // For exact calculation, we'd use inclusion-exclusion, but for typical cases:
  // - If wanting ALL M items: P = probHitOne^M
  // - If wanting fewer than M: need inclusion-exclusion

  if (wantedCount === featuredCount) {
    // Want all featured items: P = P(hit each) = probHitOne^M
    return Math.pow(probHitOne, featuredCount)
  }

  // For wanting N < M items (e.g., "want 2 of 4 featured IDs"):
  // Model each item as independently "collected" with probability probHitOne
  // P(at least N collected) = sum of P(exactly k collected) for k = N to M
  // where P(exactly k) follows Binomial(M, probHitOne)
  let probAtLeastN = 0
  for (let k = wantedCount; k <= featuredCount; k++) {
    probAtLeastN += binomialPmf(featuredCount, k, probHitOne)
  }

  return Math.min(1, Math.max(0, probAtLeastN))
}

// Keep old function name for backwards compatibility
export const calculateMultiCopyProbability = calculateAtLeastKHits

/**
 * Binomial probability mass function
 *
 * P(X = k) = C(n,k) * p^k * (1-p)^(n-k)
 *
 * @param n - Number of trials
 * @param k - Number of successes
 * @param p - Probability of success per trial
 * @returns Probability of exactly k successes
 */
function binomialPmf(n: number, k: number, p: number): number {
  if (k < 0 || k > n) {
    return 0
  }
  if (p === 0) {
    return k === 0 ? 1 : 0
  }
  if (p === 1) {
    return k === n ? 1 : 0
  }

  // Use log-space calculation to avoid overflow for large n
  const logCoeff = logBinomialCoeff(n, k)
  const logProb = k * Math.log(p) + (n - k) * Math.log(1 - p)

  return Math.exp(logCoeff + logProb)
}

/**
 * Log of binomial coefficient C(n, k) = n! / (k! * (n-k)!)
 * Uses log-gamma for numerical stability with large values
 */
function logBinomialCoeff(n: number, k: number): number {
  if (k === 0 || k === n) {
    return 0
  }
  // log(C(n,k)) = log(n!) - log(k!) - log((n-k)!)
  return logFactorial(n) - logFactorial(k) - logFactorial(n - k)
}

/**
 * Log factorial using Stirling's approximation for large values
 * Direct calculation for small values
 */
function logFactorial(n: number): number {
  if (n <= 1) {
    return 0
  }
  if (n <= 170) {
    // Direct calculation for small values (factorial fits in double precision)
    let result = 0
    for (let i = 2; i <= n; i++) {
      result += Math.log(i)
    }
    return result
  }
  // Stirling's approximation for large n
  return n * Math.log(n) - n + 0.5 * Math.log(2 * Math.PI * n)
}

/**
 * Calculate probability of obtaining all targets in N pulls
 *
 * Assumes independence between different target types.
 * P(all) = P(target1) * P(target2) * ... * P(targetN)
 *
 * @param targets - Array of targets with their rates and wanted copies
 * @param pulls - Number of pulls
 * @param featuredCounts - Mapping of featured counts per type
 * @param allEgoCollected - Whether user owns all non-rate-up EGO
 * @returns Probability of getting all targets (0-1)
 */
export function calculateMultiTargetProbability(
  targets: ExtractionTarget[],
  pulls: number,
  featuredCounts: Record<ExtractionTargetType, number>,
  allEgoCollected: boolean = false
): number {
  if (targets.length === 0) {
    return 1 // No targets = trivially satisfied
  }
  if (pulls <= 0) {
    return 0
  }

  let probability = 1
  for (const target of targets) {
    const copiesNeeded = target.wantedCopies - target.currentCopies
    if (copiesNeeded <= 0) {
      continue // Already have enough
    }

    const rate = calculateRateForTarget(target.type, featuredCounts[target.type], allEgoCollected)
    const targetProb = calculateMultiCopyProbability(pulls, rate, copiesNeeded)
    probability *= targetProb
  }

  return Math.min(1, Math.max(0, probability))
}

/**
 * Calculate pity-adjusted probability for obtaining all targets
 *
 * Pity mechanics:
 * - At 200 pulls, user can CHOOSE which featured item to claim
 * - Optimal strategy: Get M-1 items naturally, use pity on the last one
 *
 * For a single target needing 1 copy with 200+ pulls: guaranteed (100%)
 *
 * @param targets - Array of targets with their rates and wanted copies
 * @param pulls - Number of pulls (including current pity progress)
 * @param featuredCounts - Mapping of featured counts per type
 * @param currentPity - Current pity counter
 * @param allEgoCollected - Whether user owns all non-rate-up EGO
 * @returns Probability adjusted for pity guarantee (0-1)
 */
export function calculatePityAdjustedProbability(
  targets: ExtractionTarget[],
  pulls: number,
  featuredCounts: Record<ExtractionTargetType, number>,
  currentPity: number,
  allEgoCollected: boolean = false
): { probability: number; pityApplies: boolean } {
  const totalPulls = pulls + currentPity
  const totalCopiesNeeded = targets.reduce(
    (sum, t) => sum + Math.max(0, t.wantedCopies - t.currentCopies),
    0
  )

  // Check if pity can apply: reaches 200 pulls and only needs 1 more copy total
  const pityReached = totalPulls >= EXTRACTION_RATES.PITY_PULLS
  const singleCopyNeeded = totalCopiesNeeded === 1

  if (pityReached && singleCopyNeeded) {
    return { probability: 1, pityApplies: true }
  }

  // For multiple copies or not reaching pity, calculate normally
  const baseProbability = calculateMultiTargetProbability(targets, pulls, featuredCounts, allEgoCollected)

  // If pity is reached but need multiple copies, pity guarantees at least 1
  // P(all with pity) = P(get remaining M-1 naturally | have 1 from pity)
  if (pityReached && totalCopiesNeeded > 1) {
    // Reduce copies needed by 1 (pity guarantees one)
    const adjustedTargets = reduceOneFromTargets(targets)
    const pityAdjustedProb = calculateMultiTargetProbability(
      adjustedTargets,
      pulls,
      featuredCounts,
      allEgoCollected
    )
    return { probability: pityAdjustedProb, pityApplies: true }
  }

  return { probability: baseProbability, pityApplies: false }
}

/**
 * Create a copy of targets with one copy reduced from the first target that needs copies
 * Used for pity calculations where one copy is guaranteed
 */
function reduceOneFromTargets(targets: ExtractionTarget[]): ExtractionTarget[] {
  const result: ExtractionTarget[] = []
  let reduced = false

  for (const target of targets) {
    const copiesNeeded = target.wantedCopies - target.currentCopies
    if (!reduced && copiesNeeded > 0) {
      // Reduce this target by 1 copy
      result.push({
        ...target,
        currentCopies: target.currentCopies + 1,
      })
      reduced = true
    } else {
      result.push({ ...target })
    }
  }

  return result
}

/**
 * Calculate expected number of pulls to obtain at least one copy
 *
 * E[X] = 1/p for geometric distribution
 *
 * @param rate - Per-pull probability (0-1)
 * @returns Expected number of pulls (may be Infinity if rate is 0)
 */
export function calculateExpectedPulls(rate: number): number {
  if (rate <= 0) {
    return Infinity
  }
  if (rate >= 1) {
    return 1
  }
  return 1 / rate
}

/**
 * Calculate total lunacy cost for pulls
 *
 * @param pulls - Number of pulls
 * @returns Lunacy cost
 */
export function calculateLunacyCost(pulls: number): number {
  if (pulls <= 0) {
    return 0
  }
  return pulls * EXTRACTION_RATES.LUNACY_PER_PULL
}

/**
 * Calculate effective rates after applying modifiers and splitting
 *
 * @param modifiers - Banner modifier flags
 * @param featuredThreeStarCount - Number of featured 3-star IDs
 * @param featuredEgoCount - Number of featured EGO
 * @returns Effective rates per category
 */
export function calculateEffectiveRates(
  modifiers: BannerModifiers,
  featuredThreeStarCount: number,
  featuredEgoCount: number
): EffectiveRates {
  const rateTable = getEffectiveRates(modifiers.allEgoCollected, modifiers.hasAnnouncer)

  const threeStarIdEach = calculateRateForTarget('threeStarId', featuredThreeStarCount)
  // When all EGO collected: rate-up EGO gets 1.3% (doubled), NOT 0
  const egoEach = calculateRateForTarget('ego', featuredEgoCount, modifiers.allEgoCollected)
  const announcer = modifiers.hasAnnouncer
    ? calculateRateForTarget('announcer', 1) // Announcers always 1 per banner
    : 0

  return {
    threeStarIdEach,
    egoEach,
    announcer,
    threeStarIdTotal: rateTable.THREE_STAR_ID,
    // When all EGO collected, total EGO rate is 0 (no pik-tteul), but rate-up is 1.3%
    egoTotal: modifiers.allEgoCollected ? EXTRACTION_RATES.RATE_UP.EGO_ALL_COLLECTED : rateTable.EGO,
  }
}

/**
 * Calculate optimal pity allocation across targets
 *
 * Distributes pity to lowest-rate targets first (hardest to obtain naturally).
 * Returns a Map from target index to pity count allocated.
 *
 * @param targets - Array of extraction targets
 * @param pityCount - Total pity available
 * @param featuredCounts - Featured counts per type
 * @param allEgoCollected - Whether all EGO are collected
 * @returns Map<targetIndex, pityAllocated>
 */
function calculatePityAllocation(
  targets: ExtractionTarget[],
  pityCount: number,
  featuredCounts: Record<ExtractionTargetType, number>,
  allEgoCollected: boolean
): Map<number, number> {
  const allocation = new Map<number, number>()

  if (pityCount <= 0 || targets.length === 0) {
    return allocation
  }

  // Build target info with index and rate
  const targetInfos = targets.map((target, index) => {
    const wantedCount = Math.max(0, target.wantedCopies - target.currentCopies)
    let rate: number

    if (target.type === 'ego') {
      rate = allEgoCollected
        ? EXTRACTION_RATES.RATE_UP.EGO_ALL_COLLECTED
        : EXTRACTION_RATES.RATE_UP.EGO
    } else if (target.type === 'threeStarId') {
      rate = EXTRACTION_RATES.RATE_UP.THREE_STAR_ID
    } else {
      rate = EXTRACTION_RATES.RATE_UP.ANNOUNCER
    }

    return { index, wantedCount, rate }
  })

  // Sort by rate (lowest first = hardest to get = use pity first)
  const sorted = [...targetInfos].sort((a, b) => a.rate - b.rate)

  // Distribute pity
  let pityRemaining = pityCount
  for (const info of sorted) {
    if (pityRemaining <= 0 || info.wantedCount <= 0) continue

    const pityUsed = Math.min(pityRemaining, info.wantedCount)
    allocation.set(info.index, pityUsed)
    pityRemaining -= pityUsed
  }

  return allocation
}

/**
 * Main calculation entry point
 *
 * Takes user input and returns complete calculation results.
 *
 * @param input - User input configuration
 * @returns Complete calculation results
 */
export function calculateExtraction(input: ExtractionInput): ExtractionResult {
  const { plannedPulls, featuredThreeStarCount, featuredEgoCount, featuredAnnouncerCount, modifiers, targets, currentPity } =
    input

  // Featured counts for rate splitting (EGO count stays same, rate changes via allEgoCollected)
  const featuredCounts: Record<ExtractionTargetType, number> = {
    threeStarId: featuredThreeStarCount,
    ego: featuredEgoCount, // Always use actual count; rate adjustment via allEgoCollected param
    announcer: featuredAnnouncerCount,
  }

  // Pre-calculate pity info
  // IMPORTANT: Multiple pity triggers possible (every 200 pulls)
  const totalPulls = plannedPulls + currentPity
  const pityCount = Math.floor(totalPulls / EXTRACTION_RATES.PITY_PULLS)
  const totalCopiesNeeded = targets.reduce(
    (sum, t) => sum + Math.max(0, t.wantedCopies - t.currentCopies),
    0
  )

  // Pity can guarantee up to min(pityCount, totalCopiesNeeded) copies
  // User optimally allocates pity to most needed targets
  const copiesGuaranteedByPity = Math.min(pityCount, totalCopiesNeeded)
  const copiesNeededNaturally = Math.max(0, totalCopiesNeeded - copiesGuaranteedByPity)

  // Pre-calculate pity allocation for each target (rate 낮은 순으로 분배)
  // This ensures pity is distributed optimally and not duplicated
  const pityAllocation = calculatePityAllocation(targets, pityCount, featuredCounts, modifiers.allEgoCollected)

  // Calculate per-target probabilities
  // Different calculation based on extraction type:
  // - EGO: 비복원추출 (without replacement) - every hit is unique
  // - Identity/Announcer: 복원추출 (with replacement) - can get duplicates
  const targetResults: TargetProbability[] = targets.map((target, index) => {
    const wantedCount = Math.max(0, target.wantedCopies - target.currentCopies)
    const featuredCount = featuredCounts[target.type]
    const pityForThis = pityAllocation.get(index) ?? 0

    let probability: number
    let pityApplies = false
    let expectedPulls = 0

    if (wantedCount <= 0) {
      probability = 1
    } else if (target.type === 'ego') {
      // EGO: 비복원추출 - "want N EGOs" = "need at least N EGO hits"
      // Every hit is guaranteed unique, so just need enough hits total
      const egoRate = modifiers.allEgoCollected
        ? EXTRACTION_RATES.RATE_UP.EGO_ALL_COLLECTED
        : EXTRACTION_RATES.RATE_UP.EGO

      // Use pre-calculated pity allocation
      const naturalHitsNeeded = Math.max(0, wantedCount - pityForThis)

      if (naturalHitsNeeded <= 0) {
        probability = 1
        pityApplies = true
      } else {
        probability = calculateAtLeastKHits(plannedPulls, egoRate, naturalHitsNeeded)
        pityApplies = pityForThis > 0
      }

      expectedPulls = wantedCount > 0 ? (1 / egoRate) * wantedCount : 0
    } else {
      // Identity/Announcer: 복원추출 - "want N different" = Coupon Collector
      // Need to hit each specific item at least once
      const totalRate = target.type === 'threeStarId'
        ? EXTRACTION_RATES.RATE_UP.THREE_STAR_ID
        : EXTRACTION_RATES.RATE_UP.ANNOUNCER

      // Use pre-calculated pity allocation
      const naturalWanted = Math.max(0, wantedCount - pityForThis)

      if (naturalWanted <= 0) {
        probability = 1
        pityApplies = true
      } else {
        probability = calculateCouponCollectorProbability(
          plannedPulls,
          totalRate,
          featuredCount,
          naturalWanted
        )
        pityApplies = pityForThis > 0
      }

      const itemRate = totalRate / featuredCount
      expectedPulls = wantedCount > 0 ? (1 / itemRate) * wantedCount : 0
    }

    return {
      target,
      probability,
      expectedPulls,
      pityApplies,
    }
  })

  // Calculate P(any) = probability of getting at least ONE target
  // P(any) = 1 - P(none) = 1 - product of (1 - P(target))
  let anyTargetProbability: number
  if (targetResults.length === 0) {
    anyTargetProbability = 1 // No targets = trivially satisfied
  } else {
    const probNone = targetResults.reduce((acc, r) => acc * (1 - r.probability), 1)
    anyTargetProbability = 1 - probNone
  }

  // Calculate P(all) with pity optimization = probability of completing ALL targets
  let allTargetProbability: number
  if (targetResults.length === 0) {
    allTargetProbability = 1
  } else if (copiesNeededNaturally <= 0) {
    // Pity covers everything
    allTargetProbability = 1
  } else {
    allTargetProbability = calculateCombinedPityProbability(
      targets,
      plannedPulls,
      featuredCounts,
      modifiers.allEgoCollected,
      copiesGuaranteedByPity
    )
  }

  // Calculate remaining pulls until next pity (accounting for current progress)
  const pullsIntoPity = currentPity % EXTRACTION_RATES.PITY_PULLS
  const pullsUntilNextPity = EXTRACTION_RATES.PITY_PULLS - pullsIntoPity

  return {
    targetResults,
    anyTargetProbability,
    allTargetProbability,
    pityCount,
    lunacyCost: calculateLunacyCost(plannedPulls),
    pullsUntilPity: pullsUntilNextPity,
    activeRateTable: getRateTableName(modifiers.allEgoCollected, modifiers.hasAnnouncer),
  }
}

/**
 * Calculate combined probability with pity optimization for all targets
 *
 * When pity can cover some items, distribute optimally.
 * Different calculation for each type:
 * - EGO: P(at least N hits) with binomial
 * - ID/Announcer: P(collect N different) with Coupon Collector
 */
function calculateCombinedPityProbability(
  targets: ExtractionTarget[],
  pulls: number,
  featuredCounts: Record<ExtractionTargetType, number>,
  allEgoCollected: boolean,
  pityGuarantees: number
): number {
  if (targets.length === 0) return 1
  if (pulls <= 0 && pityGuarantees <= 0) return 0

  // Build target info with appropriate rates
  const targetInfos = targets.map((target) => {
    const wantedCount = Math.max(0, target.wantedCopies - target.currentCopies)
    let rate: number

    if (target.type === 'ego') {
      rate = allEgoCollected
        ? EXTRACTION_RATES.RATE_UP.EGO_ALL_COLLECTED
        : EXTRACTION_RATES.RATE_UP.EGO
    } else if (target.type === 'threeStarId') {
      rate = EXTRACTION_RATES.RATE_UP.THREE_STAR_ID
    } else {
      rate = EXTRACTION_RATES.RATE_UP.ANNOUNCER
    }

    return {
      type: target.type,
      wantedCount,
      featuredCount: featuredCounts[target.type],
      rate,
    }
  })

  // Total items needed
  const totalNeeded = targetInfos.reduce((sum, t) => sum + t.wantedCount, 0)

  if (pityGuarantees >= totalNeeded) {
    return 1 // Pity covers everything
  }

  // Distribute pity optimally (to lowest probability targets first)
  // For simplicity, sort by rate (lower rate = harder to get = use pity first)
  const sorted = [...targetInfos].sort((a, b) => a.rate - b.rate)

  let pityRemaining = pityGuarantees
  const adjustedTargets = sorted.map((t) => {
    const pityUsed = Math.min(pityRemaining, t.wantedCount)
    pityRemaining -= pityUsed
    return {
      ...t,
      wantedCount: t.wantedCount - pityUsed,
    }
  })

  // Calculate combined probability
  let probability = 1
  for (const target of adjustedTargets) {
    if (target.wantedCount <= 0) continue

    let targetProb: number
    if (target.type === 'ego') {
      // EGO: P(at least N hits) - binomial
      targetProb = calculateAtLeastKHits(pulls, target.rate, target.wantedCount)
    } else {
      // ID/Announcer: P(collect N different) - Coupon Collector
      targetProb = calculateCouponCollectorProbability(
        pulls,
        target.rate,
        target.featuredCount,
        target.wantedCount
      )
    }
    probability *= targetProb
  }

  return Math.min(1, Math.max(0, probability))
}
