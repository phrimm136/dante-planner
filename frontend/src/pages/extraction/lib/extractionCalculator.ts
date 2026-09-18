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

import { clamp01 } from '@/lib/utils'
import { EXTRACTION_RATES, type ExtractionRateTable } from './extractionRates'
import type {
  BannerModifiers,
  EffectiveRates,
  ExtractionInput,
  ExtractionResult,
  ExtractionTarget,
  ExtractionTargetType,
  TargetProbability,
} from '../types/ExtractionTypes'

type ActiveRateTable = ExtractionResult['activeRateTable']

const RATE_TABLES = {
  standard: EXTRACTION_RATES.STANDARD,
  withAnnouncer: EXTRACTION_RATES.WITH_ANNOUNCER,
  allEgoCollected: EXTRACTION_RATES.ALL_EGO_COLLECTED,
  allEgoWithAnnouncer: EXTRACTION_RATES.ALL_EGO_WITH_ANNOUNCER,
} as const satisfies Record<ActiveRateTable, ExtractionRateTable>

/**
 * Rate-up total per target type, before splitting among featured items
 *
 * EGO is the only type whose rate-up total moves with a modifier: with every
 * non-rate-up EGO already owned, the pik-tteul half of the pool folds into it.
 */
const RATE_UP_FOR: Record<ExtractionTargetType, (allEgoCollected: boolean) => number> = {
  threeStarId: () => EXTRACTION_RATES.RATE_UP.THREE_STAR_ID,
  ego: (allEgoCollected) =>
    allEgoCollected ? EXTRACTION_RATES.RATE_UP.EGO_ALL_COLLECTED : EXTRACTION_RATES.RATE_UP.EGO,
  announcer: () => EXTRACTION_RATES.RATE_UP.ANNOUNCER,
}

export function getRateTableName(allEgoCollected: boolean, hasAnnouncer: boolean): ActiveRateTable {
  if (allEgoCollected) {
    return hasAnnouncer ? 'allEgoWithAnnouncer' : 'allEgoCollected'
  }
  return hasAnnouncer ? 'withAnnouncer' : 'standard'
}

export function getEffectiveRates(
  allEgoCollected: boolean,
  hasAnnouncer: boolean,
): ExtractionRateTable {
  return RATE_TABLES[getRateTableName(allEgoCollected, hasAnnouncer)]
}

export function calculateRateForTarget(
  type: ExtractionTargetType,
  featuredCount: number,
  allEgoCollected: boolean = false,
): number {
  if (featuredCount <= 0) {
    return 0
  }

  return RATE_UP_FOR[type](allEgoCollected) / featuredCount
}

export function calculateSingleTargetProbability(pulls: number, rate: number): number {
  if (pulls <= 0 || rate <= 0) {
    return 0
  }
  if (rate >= 1) {
    return 1
  }

  const probability = 1 - Math.pow(1 - rate, pulls)

  return clamp01(probability)
}

export function calculateAtLeastKHits(pulls: number, rate: number, hitsNeeded: number): number {
  if (hitsNeeded <= 0) {
    return 1
  }
  if (pulls <= 0 || rate <= 0) {
    return 0
  }
  if (rate >= 1) {
    return pulls >= hitsNeeded ? 1 : 0
  }
  if (pulls < hitsNeeded) {
    return 0
  }

  let cumulativeLessThan = 0
  for (let k = 0; k < hitsNeeded; k++) {
    cumulativeLessThan += binomialPmf(pulls, k, rate)
  }

  const probability = 1 - cumulativeLessThan

  return clamp01(probability)
}

export function calculateCouponCollectorProbability(
  pulls: number,
  totalRate: number,
  featuredCount: number,
  wantedCount: number,
): number {
  if (wantedCount <= 0) {
    return 1
  }
  if (pulls <= 0 || totalRate <= 0 || featuredCount <= 0) {
    return 0
  }
  if (wantedCount > featuredCount) {
    return 0
  }

  const itemRate = totalRate / featuredCount

  const probHitOne = 1 - Math.pow(1 - itemRate, pulls)

  if (wantedCount === featuredCount) {
    // Math.pow and the log-space PMF below disagree by ~1 ulp on this term
    return Math.pow(probHitOne, featuredCount)
  }

  let probAtLeastN = 0
  for (let k = wantedCount; k <= featuredCount; k++) {
    probAtLeastN += binomialPmf(featuredCount, k, probHitOne)
  }

  return clamp01(probAtLeastN)
}

export function calculateNaturalProbability(
  targetType: ExtractionTargetType,
  rate: number,
  featuredCount: number,
  wantedCount: number,
  pulls: number,
): number {
  if (wantedCount <= 0) {
    return 1
  }
  if (pulls <= 0 || rate <= 0) {
    return 0
  }

  if (targetType === 'ego') {
    return calculateAtLeastKHits(pulls, rate, wantedCount)
  } else {
    return calculateCouponCollectorProbability(pulls, rate, featuredCount, wantedCount)
  }
}

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

  const logCoeff = logBinomialCoeff(n, k)
  const logProb = k * Math.log(p) + (n - k) * Math.log(1 - p)

  return Math.exp(logCoeff + logProb)
}

function logBinomialCoeff(n: number, k: number): number {
  if (k === 0 || k === n) {
    return 0
  }
  return logFactorial(n) - logFactorial(k) - logFactorial(n - k)
}

function logFactorial(n: number): number {
  if (n <= 1) {
    return 0
  }
  if (n <= 170) {
    let result = 0
    for (let i = 2; i <= n; i++) {
      result += Math.log(i)
    }
    return result
  }
  return n * Math.log(n) - n + 0.5 * Math.log(2 * Math.PI * n)
}

function calculateExactCouponCollectorPmf(
  pulls: number,
  totalRate: number,
  featuredCount: number,
  k: number,
  wantedCount: number,
): number {
  if (k < 0 || k > wantedCount) {
    return 0
  }

  const probAtLeastK =
    k === 0 ? 1 : calculateCouponCollectorProbability(pulls, totalRate, featuredCount, k)

  const probAtLeastKPlus1 =
    k >= wantedCount
      ? 0
      : calculateCouponCollectorProbability(pulls, totalRate, featuredCount, k + 1)

  return Math.max(0, probAtLeastK - probAtLeastKPlus1)
}

export function calculateCategoryDistribution(
  pulls: number,
  targetType: ExtractionTargetType,
  wantedCount: number,
  featuredCount: number,
  allEgoCollected: boolean,
): number[] {
  if (wantedCount <= 0 || pulls <= 0) {
    return [1]
  }

  const distribution: number[] = []
  const totalRate = RATE_UP_FOR[targetType](allEgoCollected)

  if (targetType === 'ego') {
    for (let k = 0; k <= wantedCount; k++) {
      if (k === wantedCount) {
        distribution.push(calculateAtLeastKHits(pulls, totalRate, k))
      } else {
        const atLeastK = k === 0 ? 1 : calculateAtLeastKHits(pulls, totalRate, k)
        const atLeastKPlus1 = calculateAtLeastKHits(pulls, totalRate, k + 1)
        distribution.push(Math.max(0, atLeastK - atLeastKPlus1))
      }
    }
  } else {
    for (let k = 0; k <= wantedCount; k++) {
      if (k === wantedCount) {
        distribution.push(calculateCouponCollectorProbability(pulls, totalRate, featuredCount, k))
      } else {
        distribution.push(
          calculateExactCouponCollectorPmf(pulls, totalRate, featuredCount, k, wantedCount),
        )
      }
    }
  }

  return distribution
}

export function convolveDistributions(dist1: number[], dist2: number[]): number[] {
  return Array.from({ length: dist1.length + dist2.length - 1 }, (_, n) => {
    let sum = 0
    for (const [i, p1] of dist1.entries()) {
      const p2 = dist2[n - i]
      if (p2 !== undefined) {
        sum += p1 * p2
      }
    }
    return sum
  })
}

export function calculateSuccessiveTargetProbabilities(
  targets: ExtractionTarget[],
  pulls: number,
  featuredCounts: Record<ExtractionTargetType, number>,
  allEgoCollected: boolean,
  pityCount: number,
): Array<{ count: number; probability: number }> {
  const totalItems = targets.reduce(
    (sum, t) => sum + Math.max(0, t.wantedCopies - t.currentCopies),
    0,
  )

  if (totalItems <= 0) {
    return []
  }

  const distributions: number[][] = []

  for (const target of targets) {
    const wantedCount = Math.max(0, target.wantedCopies - target.currentCopies)
    if (wantedCount <= 0) continue

    const dist = calculateCategoryDistribution(
      pulls,
      target.type,
      wantedCount,
      featuredCounts[target.type],
      allEgoCollected,
    )
    distributions.push(dist)
  }

  const [firstDistribution, ...remainingDistributions] = distributions
  if (firstDistribution === undefined) {
    return []
  }

  let totalDistribution = firstDistribution
  for (const distribution of remainingDistributions) {
    totalDistribution = convolveDistributions(totalDistribution, distribution)
  }

  const results: Array<{ count: number; probability: number }> = []

  for (let k = totalItems; k >= 1; k--) {
    const naturalRequirement = Math.max(0, k - pityCount)

    const probability = totalDistribution
      .slice(naturalRequirement)
      .reduce((sum, bucketProbability) => sum + bucketProbability, 0)

    results.push({
      count: k,
      probability: clamp01(probability),
    })
  }

  return results
}

export function calculateMultiTargetProbability(
  targets: ExtractionTarget[],
  pulls: number,
  featuredCounts: Record<ExtractionTargetType, number>,
  allEgoCollected: boolean = false,
): number {
  if (targets.length === 0) {
    return 1
  }
  if (pulls <= 0) {
    return 0
  }

  let probability = 1
  for (const target of targets) {
    const copiesNeeded = target.wantedCopies - target.currentCopies
    if (copiesNeeded <= 0) {
      continue
    }

    const rate = calculateRateForTarget(target.type, featuredCounts[target.type], allEgoCollected)
    const targetProb = calculateAtLeastKHits(pulls, rate, copiesNeeded)
    probability *= targetProb
  }

  return clamp01(probability)
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
  allEgoCollected: boolean = false,
): { probability: number; pityApplies: boolean } {
  const totalPulls = pulls + currentPity
  const totalCopiesNeeded = targets.reduce(
    (sum, t) => sum + Math.max(0, t.wantedCopies - t.currentCopies),
    0,
  )

  const pityReached = totalPulls >= EXTRACTION_RATES.PITY_PULLS

  if (pityReached && totalCopiesNeeded > 0) {
    const probability =
      totalCopiesNeeded === 1
        ? 1
        : calculateMultiTargetProbability(
            reduceOneFromTargets(targets),
            pulls,
            featuredCounts,
            allEgoCollected,
          )
    return { probability, pityApplies: true }
  }

  return {
    probability: calculateMultiTargetProbability(targets, pulls, featuredCounts, allEgoCollected),
    pityApplies: false,
  }
}

function reduceOneFromTargets(targets: ExtractionTarget[]): ExtractionTarget[] {
  const result: ExtractionTarget[] = []
  let reduced = false

  for (const target of targets) {
    const copiesNeeded = target.wantedCopies - target.currentCopies
    if (!reduced && copiesNeeded > 0) {
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

export function calculateExpectedPulls(rate: number): number {
  if (rate <= 0) {
    return Infinity
  }
  if (rate >= 1) {
    return 1
  }
  return 1 / rate
}

export function calculateLunacyCost(pulls: number): number {
  if (pulls <= 0) {
    return 0
  }
  return pulls * EXTRACTION_RATES.LUNACY_PER_PULL
}

export function calculateEffectiveRates(
  modifiers: BannerModifiers,
  featuredThreeStarCount: number,
  featuredEgoCount: number,
): EffectiveRates {
  const rateTable = getEffectiveRates(modifiers.allEgoCollected, modifiers.hasAnnouncer)

  const threeStarIdEach = calculateRateForTarget('threeStarId', featuredThreeStarCount)
  // When all EGO collected: rate-up EGO gets 1.3% (doubled), NOT 0
  const egoEach = calculateRateForTarget('ego', featuredEgoCount, modifiers.allEgoCollected)
  const announcer = modifiers.hasAnnouncer ? calculateRateForTarget('announcer', 1) : 0

  return {
    threeStarIdEach,
    egoEach,
    announcer,
    threeStarIdTotal: rateTable.THREE_STAR_ID,
    // When all EGO collected, total EGO rate is 0 (no pik-tteul), but rate-up is 1.3%
    egoTotal: modifiers.allEgoCollected
      ? EXTRACTION_RATES.RATE_UP.EGO_ALL_COLLECTED
      : rateTable.EGO,
  }
}

function calculatePityAllocation(
  targets: ExtractionTarget[],
  pityCount: number,
  featuredCounts: Record<ExtractionTargetType, number>,
  allEgoCollected: boolean,
  pulls: number,
): Map<number, number> {
  const allocation = new Map<number, number>()

  if (pityCount <= 0 || targets.length === 0) {
    return allocation
  }

  const targetInfos = targets.map((target, index) => {
    const wantedCount = Math.max(0, target.wantedCopies - target.currentCopies)
    const rate = RATE_UP_FOR[target.type](allEgoCollected)

    const naturalProb = calculateNaturalProbability(
      target.type,
      rate,
      featuredCounts[target.type],
      wantedCount,
      pulls,
    )

    return { index, wantedCount, rate, naturalProb }
  })

  const sorted = [...targetInfos].sort((a, b) => a.naturalProb - b.naturalProb)

  let pityRemaining = pityCount
  for (const info of sorted) {
    if (pityRemaining <= 0 || info.wantedCount <= 0) continue

    const pityUsed = Math.min(pityRemaining, info.wantedCount)
    allocation.set(info.index, pityUsed)
    pityRemaining -= pityUsed
  }

  return allocation
}

interface TargetProbabilityContext {
  pulls: number
  featuredCounts: Record<ExtractionTargetType, number>
  allEgoCollected: boolean
  pityAllocated: number
}

function computeTargetProbability(
  target: ExtractionTarget,
  ctx: TargetProbabilityContext,
): TargetProbability {
  const wantedCount = Math.max(0, target.wantedCopies - target.currentCopies)

  if (wantedCount <= 0) {
    return { target, probability: 1, expectedPulls: 0, pityApplies: false }
  }

  const isEgo = target.type === 'ego'
  const featuredCount = ctx.featuredCounts[target.type]

  // No featured item of this type means no pull can produce one. Dividing by the
  // count instead yields an item rate of Infinity and, from it, zero expected
  // pulls — a certainty reported for something that cannot happen.
  if (!isEgo && featuredCount <= 0) {
    return { target, probability: 0, expectedPulls: Infinity, pityApplies: false }
  }

  const totalRate = RATE_UP_FOR[target.type](ctx.allEgoCollected)
  const itemRate = isEgo ? totalRate : totalRate / featuredCount

  const naturalWanted = Math.max(0, wantedCount - ctx.pityAllocated)

  let probability = 1
  if (naturalWanted > 0) {
    probability = isEgo
      ? calculateAtLeastKHits(ctx.pulls, totalRate, naturalWanted)
      : calculateCouponCollectorProbability(ctx.pulls, totalRate, featuredCount, naturalWanted)
  }

  return {
    target,
    probability,
    expectedPulls: (1 / itemRate) * wantedCount,
    pityApplies: ctx.pityAllocated > 0,
  }
}

export function calculateExtraction(input: ExtractionInput): ExtractionResult {
  const {
    plannedPulls,
    featuredThreeStarCount,
    featuredEgoCount,
    featuredAnnouncerCount,
    modifiers,
    targets,
    currentPity,
  } = input

  const featuredCounts: Record<ExtractionTargetType, number> = {
    threeStarId: featuredThreeStarCount,
    ego: featuredEgoCount, // Always use actual count; rate adjustment via allEgoCollected param
    announcer: featuredAnnouncerCount,
  }

  const totalPulls = plannedPulls + currentPity
  const pityCount = Math.floor(totalPulls / EXTRACTION_RATES.PITY_PULLS)
  const totalCopiesNeeded = targets.reduce(
    (sum, t) => sum + Math.max(0, t.wantedCopies - t.currentCopies),
    0,
  )

  const copiesGuaranteedByPity = Math.min(pityCount, totalCopiesNeeded)
  const copiesNeededNaturally = Math.max(0, totalCopiesNeeded - copiesGuaranteedByPity)

  const pityAllocation = calculatePityAllocation(
    targets,
    pityCount,
    featuredCounts,
    modifiers.allEgoCollected,
    plannedPulls,
  )

  const targetResults: TargetProbability[] = targets.map((target, index) =>
    computeTargetProbability(target, {
      pulls: plannedPulls,
      featuredCounts,
      allEgoCollected: modifiers.allEgoCollected,
      pityAllocated: pityAllocation.get(index) ?? 0,
    }),
  )

  let anyTargetProbability: number
  if (targetResults.length === 0) {
    anyTargetProbability = 1
  } else {
    const probNone = targetResults.reduce((acc, r) => acc * (1 - r.probability), 1)
    anyTargetProbability = 1 - probNone
  }

  let allTargetProbability: number
  if (targetResults.length === 0) {
    allTargetProbability = 1
  } else if (copiesNeededNaturally <= 0) {
    allTargetProbability = 1
  } else {
    allTargetProbability = calculateCombinedPityProbability(
      targets,
      plannedPulls,
      featuredCounts,
      modifiers.allEgoCollected,
      copiesGuaranteedByPity,
    )
  }

  const pullsIntoPity = currentPity % EXTRACTION_RATES.PITY_PULLS
  const pullsUntilNextPity = EXTRACTION_RATES.PITY_PULLS - pullsIntoPity

  const successiveProbabilities = calculateSuccessiveTargetProbabilities(
    targets,
    plannedPulls,
    featuredCounts,
    modifiers.allEgoCollected,
    pityCount,
  )

  return {
    targetResults,
    anyTargetProbability,
    allTargetProbability,
    successiveProbabilities,
    totalItemsWanted: totalCopiesNeeded,
    pityCount,
    lunacyCost: calculateLunacyCost(plannedPulls),
    pullsUntilPity: pullsUntilNextPity,
    activeRateTable: getRateTableName(modifiers.allEgoCollected, modifiers.hasAnnouncer),
  }
}

function calculateCombinedPityProbability(
  targets: ExtractionTarget[],
  pulls: number,
  featuredCounts: Record<ExtractionTargetType, number>,
  allEgoCollected: boolean,
  pityGuarantees: number,
): number {
  if (targets.length === 0) return 1
  if (pulls <= 0 && pityGuarantees <= 0) return 0

  const targetInfos = targets.map((target) => {
    const wantedCount = Math.max(0, target.wantedCopies - target.currentCopies)
    const rate = RATE_UP_FOR[target.type](allEgoCollected)

    const naturalProb = calculateNaturalProbability(
      target.type,
      rate,
      featuredCounts[target.type],
      wantedCount,
      pulls,
    )

    return {
      type: target.type,
      wantedCount,
      featuredCount: featuredCounts[target.type],
      rate,
      naturalProb,
    }
  })

  const totalNeeded = targetInfos.reduce((sum, t) => sum + t.wantedCount, 0)

  if (pityGuarantees >= totalNeeded) {
    return 1
  }

  const sorted = [...targetInfos].sort((a, b) => a.naturalProb - b.naturalProb)

  let pityRemaining = pityGuarantees
  const adjustedTargets = sorted.map((t) => {
    const pityUsed = Math.min(pityRemaining, t.wantedCount)
    pityRemaining -= pityUsed
    return {
      ...t,
      wantedCount: t.wantedCount - pityUsed,
    }
  })

  let probability = 1
  for (const target of adjustedTargets) {
    if (target.wantedCount <= 0) continue

    let targetProb: number
    if (target.type === 'ego') {
      targetProb = calculateAtLeastKHits(pulls, target.rate, target.wantedCount)
    } else {
      targetProb = calculateCouponCollectorProbability(
        pulls,
        target.rate,
        target.featuredCount,
        target.wantedCount,
      )
    }
    probability *= targetProb
  }

  return clamp01(probability)
}
