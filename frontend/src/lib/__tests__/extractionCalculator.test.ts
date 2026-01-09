/**
 * extractionCalculator.test.ts
 *
 * Unit tests for extraction (gacha) probability calculator functions.
 * Tests rate calculations, probability math, pity mechanics, and edge cases.
 */

import { describe, it, expect } from 'vitest'
import {
  getEffectiveRates,
  getRateTableName,
  calculateRateForTarget,
  calculateSingleTargetProbability,
  calculateMultiCopyProbability,
  calculateMultiTargetProbability,
  calculatePityAdjustedProbability,
  calculateExpectedPulls,
  calculateLunacyCost,
  calculateEffectiveRates,
  calculateExtraction,
  calculateCategoryDistribution,
  convolveDistributions,
  calculateSuccessiveTargetProbabilities,
} from '../extractionCalculator'
import { EXTRACTION_RATES } from '@/lib/constants'
import type { ExtractionInput, ExtractionTarget } from '@/types/ExtractionTypes'

describe('getEffectiveRates', () => {
  it('returns standard rates when no modifiers', () => {
    const rates = getEffectiveRates(false, false)
    expect(rates).toEqual(EXTRACTION_RATES.STANDARD)
  })

  it('returns with announcer rates when hasAnnouncer is true', () => {
    const rates = getEffectiveRates(false, true)
    expect(rates).toEqual(EXTRACTION_RATES.WITH_ANNOUNCER)
  })

  it('returns all EGO collected rates when allEgoCollected is true', () => {
    const rates = getEffectiveRates(true, false)
    expect(rates).toEqual(EXTRACTION_RATES.ALL_EGO_COLLECTED)
  })

  it('returns combined rates when both modifiers are true', () => {
    const rates = getEffectiveRates(true, true)
    expect(rates).toEqual(EXTRACTION_RATES.ALL_EGO_WITH_ANNOUNCER)
  })
})

describe('getRateTableName', () => {
  it('returns "standard" for no modifiers', () => {
    expect(getRateTableName(false, false)).toBe('standard')
  })

  it('returns "withAnnouncer" when only hasAnnouncer', () => {
    expect(getRateTableName(false, true)).toBe('withAnnouncer')
  })

  it('returns "allEgoCollected" when only allEgoCollected', () => {
    expect(getRateTableName(true, false)).toBe('allEgoCollected')
  })

  it('returns "allEgoWithAnnouncer" when both modifiers', () => {
    expect(getRateTableName(true, true)).toBe('allEgoWithAnnouncer')
  })
})

describe('calculateRateForTarget', () => {
  describe('3-star Identity rates', () => {
    it('returns full rate-up for single featured ID', () => {
      expect(calculateRateForTarget('threeStarId', 1)).toBe(0.0145)
    })

    it('splits rate evenly among 2 featured IDs', () => {
      expect(calculateRateForTarget('threeStarId', 2)).toBe(0.00725)
    })

    it('splits rate evenly among 3 featured IDs', () => {
      const rate = calculateRateForTarget('threeStarId', 3)
      expect(rate).toBeCloseTo(0.00483, 5) // 0.0145 / 3 = 0.004833...
    })
  })

  describe('EGO rates', () => {
    it('returns full rate-up for single featured EGO (standard)', () => {
      expect(calculateRateForTarget('ego', 1)).toBe(0.0065)
    })

    it('splits rate evenly among 2 featured EGO (standard)', () => {
      expect(calculateRateForTarget('ego', 2)).toBe(0.00325)
    })

    it('returns DOUBLED rate for EGO when allEgoCollected (1.3% instead of 0.65%)', () => {
      // All EGO collected means no pik-tteul, so rate-up gets full 1.3%
      expect(calculateRateForTarget('ego', 1, true)).toBe(0.013)
    })

    it('splits doubled rate among 2 EGO when allEgoCollected', () => {
      expect(calculateRateForTarget('ego', 2, true)).toBe(0.0065)
    })
  })

  describe('Announcer rates', () => {
    it('returns full rate for single announcer', () => {
      expect(calculateRateForTarget('announcer', 1)).toBe(0.013)
    })
  })

  describe('edge cases', () => {
    it('returns 0 for zero featured count', () => {
      expect(calculateRateForTarget('threeStarId', 0)).toBe(0)
    })

    it('returns 0 for negative featured count', () => {
      expect(calculateRateForTarget('threeStarId', -1)).toBe(0)
    })
  })
})

describe('calculateSingleTargetProbability', () => {
  describe('standard probability calculations', () => {
    it('calculates ~76.5% for 100 pulls at 0.0145 rate', () => {
      const probability = calculateSingleTargetProbability(100, 0.0145)
      // P = 1 - (1 - 0.0145)^100 = 1 - 0.9855^100 ≈ 0.765
      expect(probability).toBeCloseTo(0.765, 2)
    })

    it('calculates ~94.4% for 200 pulls at 0.0145 rate', () => {
      const probability = calculateSingleTargetProbability(200, 0.0145)
      // P = 1 - (1 - 0.0145)^200 ≈ 0.944
      expect(probability).toBeCloseTo(0.944, 2)
    })

    it('calculates ~50% for 48 pulls at 0.0145 rate', () => {
      // ln(0.5) / ln(1-0.0145) ≈ 47.4, so 48 pulls should be just over 50%
      const probability = calculateSingleTargetProbability(48, 0.0145)
      expect(probability).toBeGreaterThan(0.5)
      expect(probability).toBeLessThan(0.51)
    })
  })

  describe('edge cases', () => {
    it('returns 0 for zero pulls', () => {
      expect(calculateSingleTargetProbability(0, 0.0145)).toBe(0)
    })

    it('returns 0 for negative pulls', () => {
      expect(calculateSingleTargetProbability(-10, 0.0145)).toBe(0)
    })

    it('returns 0 for zero rate', () => {
      expect(calculateSingleTargetProbability(100, 0)).toBe(0)
    })

    it('returns 0 for negative rate', () => {
      expect(calculateSingleTargetProbability(100, -0.01)).toBe(0)
    })

    it('returns 1 for rate of 1 (100%)', () => {
      expect(calculateSingleTargetProbability(1, 1)).toBe(1)
    })

    it('returns 1 for rate greater than 1', () => {
      expect(calculateSingleTargetProbability(1, 1.5)).toBe(1)
    })

    it('handles very large pull counts without overflow', () => {
      const probability = calculateSingleTargetProbability(1000, 0.0145)
      expect(probability).toBeGreaterThan(0.999)
      expect(probability).toBeLessThanOrEqual(1)
    })

    it('handles very small rates', () => {
      const probability = calculateSingleTargetProbability(100, 0.0001)
      expect(probability).toBeCloseTo(0.01, 2)
    })
  })
})

describe('calculateMultiCopyProbability', () => {
  describe('standard calculations', () => {
    it('returns same as single target for 1 copy', () => {
      const single = calculateSingleTargetProbability(100, 0.0145)
      const multi = calculateMultiCopyProbability(100, 0.0145, 1)
      expect(multi).toBeCloseTo(single, 5)
    })

    it('returns lower probability for multiple copies', () => {
      const oneCopy = calculateMultiCopyProbability(100, 0.0145, 1)
      const twoCopies = calculateMultiCopyProbability(100, 0.0145, 2)
      expect(twoCopies).toBeLessThan(oneCopy)
    })

    it('calculates correctly for 2 copies at 100 pulls', () => {
      // This is more complex binomial calculation
      const probability = calculateMultiCopyProbability(100, 0.0145, 2)
      expect(probability).toBeGreaterThan(0.3)
      expect(probability).toBeLessThan(0.5)
    })
  })

  describe('edge cases', () => {
    it('returns 1 for 0 copies wanted (trivially satisfied)', () => {
      expect(calculateMultiCopyProbability(100, 0.0145, 0)).toBe(1)
    })

    it('returns 0 for zero pulls when copies wanted', () => {
      expect(calculateMultiCopyProbability(0, 0.0145, 1)).toBe(0)
    })

    it('returns 0 when pulls less than copies wanted', () => {
      expect(calculateMultiCopyProbability(2, 0.0145, 3)).toBe(0)
    })

    it('returns 0 for zero rate when copies wanted', () => {
      expect(calculateMultiCopyProbability(100, 0, 1)).toBe(0)
    })

    it('returns 1 for 100% rate with enough pulls', () => {
      expect(calculateMultiCopyProbability(10, 1, 5)).toBe(1)
    })

    it('returns 0 for 100% rate with not enough pulls', () => {
      expect(calculateMultiCopyProbability(3, 1, 5)).toBe(0)
    })
  })
})

describe('calculateMultiTargetProbability', () => {
  const makeFeaturedCounts = (id: number, ego: number, announcer: number) => ({
    threeStarId: id,
    ego,
    announcer,
  })

  describe('standard calculations', () => {
    it('returns 1 for empty targets (trivially satisfied)', () => {
      const probability = calculateMultiTargetProbability([], 100, makeFeaturedCounts(2, 1, 0))
      expect(probability).toBe(1)
    })

    it('calculates correctly for single target', () => {
      const target: ExtractionTarget = {
        type: 'threeStarId',
        wantedCopies: 1,
        currentCopies: 0,
      }
      const probability = calculateMultiTargetProbability([target], 100, makeFeaturedCounts(2, 1, 0))
      // Rate = 0.0145 / 2 = 0.00725
      // P = 1 - (1 - 0.00725)^100 ≈ 0.518
      expect(probability).toBeCloseTo(0.518, 2)
    })

    it('multiplies probabilities for multiple independent targets', () => {
      const targets: ExtractionTarget[] = [
        { type: 'threeStarId', wantedCopies: 1, currentCopies: 0 },
        { type: 'ego', wantedCopies: 1, currentCopies: 0 },
      ]
      const probability = calculateMultiTargetProbability(targets, 100, makeFeaturedCounts(1, 1, 0))
      // ID rate = 0.0145, EGO rate = 0.0065
      // P(ID) ≈ 0.765, P(EGO) ≈ 0.479
      // P(both) ≈ 0.765 * 0.479 ≈ 0.366
      expect(probability).toBeCloseTo(0.366, 2)
    })
  })

  describe('edge cases', () => {
    it('returns 0 for zero pulls', () => {
      const target: ExtractionTarget = {
        type: 'threeStarId',
        wantedCopies: 1,
        currentCopies: 0,
      }
      expect(calculateMultiTargetProbability([target], 0, makeFeaturedCounts(1, 0, 0))).toBe(0)
    })

    it('skips targets that already have enough copies', () => {
      const target: ExtractionTarget = {
        type: 'threeStarId',
        wantedCopies: 1,
        currentCopies: 1, // Already have it
      }
      expect(calculateMultiTargetProbability([target], 100, makeFeaturedCounts(1, 0, 0))).toBe(1)
    })
  })
})

describe('calculatePityAdjustedProbability', () => {
  const makeFeaturedCounts = (id: number, ego: number, announcer: number) => ({
    threeStarId: id,
    ego,
    announcer,
  })

  describe('pity guarantee', () => {
    it('returns 100% for single target with 200 pulls (pity)', () => {
      const target: ExtractionTarget = {
        type: 'threeStarId',
        wantedCopies: 1,
        currentCopies: 0,
      }
      const result = calculatePityAdjustedProbability([target], 200, makeFeaturedCounts(1, 0, 0), 0)
      expect(result.probability).toBe(1)
      expect(result.pityApplies).toBe(true)
    })

    it('returns 100% with pity counter reaching 200', () => {
      const target: ExtractionTarget = {
        type: 'threeStarId',
        wantedCopies: 1,
        currentCopies: 0,
      }
      // 150 planned pulls + 50 current pity = 200 total
      const result = calculatePityAdjustedProbability([target], 150, makeFeaturedCounts(1, 0, 0), 50)
      expect(result.probability).toBe(1)
      expect(result.pityApplies).toBe(true)
    })
  })

  describe('no pity', () => {
    it('returns natural probability when not reaching 200', () => {
      const target: ExtractionTarget = {
        type: 'threeStarId',
        wantedCopies: 1,
        currentCopies: 0,
      }
      const result = calculatePityAdjustedProbability([target], 100, makeFeaturedCounts(1, 0, 0), 0)
      expect(result.probability).toBeLessThan(1)
      expect(result.pityApplies).toBe(false)
    })

    it('does not apply pity for multiple copies needed', () => {
      const target: ExtractionTarget = {
        type: 'threeStarId',
        wantedCopies: 2, // Need 2 copies
        currentCopies: 0,
      }
      const result = calculatePityAdjustedProbability([target], 200, makeFeaturedCounts(1, 0, 0), 0)
      expect(result.probability).toBeLessThan(1)
      expect(result.pityApplies).toBe(true) // Pity applies but doesn't guarantee 100%
    })
  })
})

describe('calculateExpectedPulls', () => {
  it('calculates expected pulls for single target', () => {
    // E[X] = 1/rate for geometric distribution
    const expected = calculateExpectedPulls(0.0145)
    expect(expected).toBeCloseTo(68.97, 1) // 1 / 0.0145 ≈ 68.97
  })

  it('returns Infinity for zero rate', () => {
    expect(calculateExpectedPulls(0)).toBe(Infinity)
  })

  it('returns 1 for 100% rate', () => {
    expect(calculateExpectedPulls(1)).toBe(1)
  })

  it('returns Infinity for negative rate', () => {
    expect(calculateExpectedPulls(-0.01)).toBe(Infinity)
  })
})

describe('calculateLunacyCost', () => {
  it('calculates correctly for 100 pulls', () => {
    expect(calculateLunacyCost(100)).toBe(13000)
  })

  it('calculates correctly for 200 pulls (pity)', () => {
    expect(calculateLunacyCost(200)).toBe(26000)
  })

  it('returns 0 for zero pulls', () => {
    expect(calculateLunacyCost(0)).toBe(0)
  })

  it('returns 0 for negative pulls', () => {
    expect(calculateLunacyCost(-10)).toBe(0)
  })

  it('uses correct rate constant', () => {
    expect(calculateLunacyCost(1)).toBe(EXTRACTION_RATES.LUNACY_PER_PULL)
  })
})

describe('calculateEffectiveRates', () => {
  it('calculates correct per-item rates with 2 featured IDs', () => {
    const rates = calculateEffectiveRates(
      { allEgoCollected: false, hasAnnouncer: false },
      2,
      1
    )
    expect(rates.threeStarIdEach).toBe(0.00725) // 0.0145 / 2
    expect(rates.egoEach).toBe(0.0065) // 0.0065 / 1
    expect(rates.announcer).toBe(0)
    expect(rates.threeStarIdTotal).toBe(0.029) // From standard table
    expect(rates.egoTotal).toBe(0.013)
  })

  it('returns DOUBLED EGO rate when all EGO collected (no pik-tteul, full 1.3% to rate-up)', () => {
    const rates = calculateEffectiveRates(
      { allEgoCollected: true, hasAnnouncer: false },
      1,
      1
    )
    // When all EGO collected: rate-up gets full 1.3% instead of 0.65%
    expect(rates.egoEach).toBe(0.013) // Full 1.3% for single featured EGO
    expect(rates.egoTotal).toBe(0.013) // Total EGO rate is 1.3% (all goes to rate-up)
  })

  it('includes announcer rate when hasAnnouncer is true', () => {
    const rates = calculateEffectiveRates(
      { allEgoCollected: false, hasAnnouncer: true },
      1,
      1
    )
    expect(rates.announcer).toBe(0.013)
  })
})

describe('calculateExtraction', () => {
  describe('complete calculation flow', () => {
    it('returns correct result structure', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 2,
        featuredEgoCount: 1,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 1, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)

      expect(result).toHaveProperty('targetResults')
      expect(result).toHaveProperty('anyTargetProbability')
      expect(result).toHaveProperty('lunacyCost')
      expect(result).toHaveProperty('pullsUntilPity')
      expect(result).toHaveProperty('activeRateTable')
    })

    it('calculates lunacy cost correctly', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.lunacyCost).toBe(13000)
    })

    it('calculates pulls until pity correctly', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [],
        currentPity: 50,
      }

      const result = calculateExtraction(input)
      expect(result.pullsUntilPity).toBe(150) // 200 - 50
    })

    it('correctly identifies active rate table', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 1,
        modifiers: { allEgoCollected: true, hasAnnouncer: true },
        targets: [],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.activeRateTable).toBe('allEgoWithAnnouncer')
    })
  })

  describe('target probability calculations', () => {
    it('returns correct probability for single ID target', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 1, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.targetResults).toHaveLength(1)
      expect(result.targetResults[0].probability).toBeCloseTo(0.765, 2)
    })

    it('returns 100% with pity for single target', () => {
      const input: ExtractionInput = {
        plannedPulls: 200,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 1, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.targetResults[0].probability).toBe(1)
      expect(result.targetResults[0].pityApplies).toBe(true)
    })
  })

  describe('any target probability', () => {
    it('returns 1 for empty targets', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.anyTargetProbability).toBe(1)
    })

    it('calculates P(any) as 1 - P(none)', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 1,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [
          { type: 'threeStarId', wantedCopies: 1, currentCopies: 0 },
          { type: 'ego', wantedCopies: 1, currentCopies: 0 },
        ],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      // P(any) should be higher than each individual
      expect(result.anyTargetProbability).toBeGreaterThan(result.targetResults[0].probability)
      expect(result.anyTargetProbability).toBeGreaterThan(result.targetResults[1].probability)
    })
  })

  describe('rate-up splitting edge cases', () => {
    it('calculates P(at least 1 of 3 featured IDs) with Coupon Collector model', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 3,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 1, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      // Identity uses 복원추출 (with replacement) - Coupon Collector model
      // "want 1 from 3" = want ANY 1 of the 3 featured items
      // itemRate = 0.0145 / 3 ≈ 0.00483
      // P(collect specific item) = 1 - (1 - 0.00483)^100 ≈ 0.385
      // P(collect at least 1 of 3) = 1 - P(miss all 3) = 1 - (1-0.385)^3 ≈ 0.767
      expect(result.targetResults[0].probability).toBeCloseTo(0.767, 2)
    })

    it('calculates P(all 3 featured IDs) correctly', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 3,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 3, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      // "want all 3" = want each specific item at least once
      // P(collect specific item) ≈ 0.385
      // P(collect all 3) = 0.385^3 ≈ 0.057
      expect(result.targetResults[0].probability).toBeCloseTo(0.057, 2)
    })
  })

  describe('edge cases', () => {
    it('handles zero pulls correctly', () => {
      const input: ExtractionInput = {
        plannedPulls: 0,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 1, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.targetResults[0].probability).toBe(0)
      expect(result.lunacyCost).toBe(0)
    })

    it('handles already owned target', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 1, currentCopies: 1 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.targetResults[0].probability).toBe(1) // Already have it
    })

    it('handles very large pull counts (1000+) without overflow', () => {
      const input: ExtractionInput = {
        plannedPulls: 1000,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 1, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.targetResults[0].probability).toBeGreaterThan(0.999)
      expect(result.targetResults[0].probability).toBeLessThanOrEqual(1)
      expect(result.lunacyCost).toBe(130000)
    })

    it('calculates pulls until NEXT pity based on cycle position', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [],
        currentPity: 50, // 50 pulls into current pity cycle
      }

      const result = calculateExtraction(input)
      // 50 into cycle, 200 - 50 = 150 until next pity
      expect(result.pullsUntilPity).toBe(150)
      // With 100 planned + 50 current = 150 total, no full pity reached
      expect(result.pityCount).toBe(0)
    })

    it('calculates 4 EGOs @ 400 pulls with allEgoCollected (비복원추출)', () => {
      // User scenario: want 4 different EGOs from 4 featured, with all non-rate-up EGO collected
      // EGO uses 비복원추출 - every hit is guaranteed unique
      const input: ExtractionInput = {
        plannedPulls: 400,
        featuredThreeStarCount: 0,
        featuredEgoCount: 4,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: true, hasAnnouncer: false },
        targets: [{ type: 'ego', wantedCopies: 4, currentCopies: 0 }], // Single target: want 4 different EGOs
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      // 400 pulls = 2 pity triggers (at 200 and 400)
      expect(result.pityCount).toBe(2)
      expect(result.targetResults).toHaveLength(1)

      // With 비복원추출 and 2 pity:
      // - Pity guarantees 2 EGOs
      // - Need at least 2 natural EGO hits at 1.3% rate
      // - P(X ≥ 2) where X ~ Binomial(400, 0.013)
      // - Expected = 5.2 hits
      // - P(X ≥ 2) ≈ 96.7%
      expect(result.targetResults[0].probability).toBeGreaterThan(0.95)
      expect(result.targetResults[0].probability).toBeLessThanOrEqual(1)
      expect(result.targetResults[0].pityApplies).toBe(true)
    })

    it('calculates EGO without pity (비복원추출)', () => {
      // No pity: need all 4 hits naturally
      const input: ExtractionInput = {
        plannedPulls: 100, // Less than 200, no pity
        featuredThreeStarCount: 0,
        featuredEgoCount: 4,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: true, hasAnnouncer: false },
        targets: [{ type: 'ego', wantedCopies: 4, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.pityCount).toBe(0)

      // Need 4 EGO hits in 100 pulls at 1.3%
      // P(X ≥ 4) where X ~ Binomial(100, 0.013)
      // Expected = 1.3 hits
      // This is quite low - around 2-5%
      expect(result.targetResults[0].probability).toBeLessThan(0.1)
      expect(result.targetResults[0].pityApplies).toBe(false)
    })

    it('calculates correctly with 3+ pity triggers (600 pulls)', () => {
      // 600 pulls = 3 pity triggers
      const input: ExtractionInput = {
        plannedPulls: 600,
        featuredThreeStarCount: 0,
        featuredEgoCount: 4,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: true, hasAnnouncer: false },
        targets: [{ type: 'ego', wantedCopies: 4, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      // 600 pulls = 3 pity triggers
      expect(result.pityCount).toBe(3)

      // With 3 pity covering 3 EGOs, need only 1 natural EGO hit
      // P(X ≥ 1) at 1.3% over 600 pulls is very high (~99.96%)
      expect(result.targetResults[0].probability).toBeGreaterThan(0.99)
      expect(result.targetResults[0].pityApplies).toBe(true)
    })

    it('calculates correctly with 5 pity triggers (1000 pulls)', () => {
      // Edge case: more pity than needed
      const input: ExtractionInput = {
        plannedPulls: 1000,
        featuredThreeStarCount: 0,
        featuredEgoCount: 4,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: true, hasAnnouncer: false },
        targets: [{ type: 'ego', wantedCopies: 4, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      // 1000 pulls = 5 pity triggers
      expect(result.pityCount).toBe(5)

      // With 5 pity but only 4 EGOs wanted, pity covers all 4
      // Should be 100% probability
      expect(result.targetResults[0].probability).toBe(1)
      expect(result.targetResults[0].pityApplies).toBe(true)
    })

    it('calculates multiple announcers correctly (bug fix verification)', () => {
      // Bug scenario: 2 featured announcers, want 2 - was returning 0% due to hardcoded featuredCount=1
      const input: ExtractionInput = {
        plannedPulls: 199,
        featuredThreeStarCount: 0,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 2, // Now properly passed through
        modifiers: { allEgoCollected: false, hasAnnouncer: true },
        targets: [{ type: 'announcer', wantedCopies: 2, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      // With 2 featured announcers at 1.3% total rate:
      // itemRate = 0.013 / 2 = 0.0065
      // P(collect specific item) = 1 - (1 - 0.0065)^199 ≈ 0.727
      // P(collect both) = 0.727^2 ≈ 0.528
      expect(result.targetResults[0].probability).toBeGreaterThan(0.5)
      expect(result.targetResults[0].probability).toBeLessThan(0.6)
    })

    it('distributes pity correctly across multiple targets (bug fix verification)', () => {
      // Bug scenario: 200 pulls = 1 pity, but pity was applied to ALL targets
      // Expected: pity should be distributed optimally (LOWEST NATURAL PROBABILITY first)
      const input: ExtractionInput = {
        plannedPulls: 200,
        featuredThreeStarCount: 2,
        featuredEgoCount: 1,
        featuredAnnouncerCount: 2,
        modifiers: { allEgoCollected: true, hasAnnouncer: true },
        targets: [
          { type: 'threeStarId', wantedCopies: 2, currentCopies: 0 },
          { type: 'ego', wantedCopies: 1, currentCopies: 0 },
          { type: 'announcer', wantedCopies: 2, currentCopies: 0 },
        ],
        currentPity: 0,
      }

      const result = calculateExtraction(input)

      // Only 1 pity available for 3 targets
      expect(result.pityCount).toBe(1)

      // Count how many targets have pityApplies = true (should be exactly 1)
      const pitiedTargets = result.targetResults.filter(r => r.pityApplies)
      expect(pitiedTargets.length).toBe(1)

      // ANNOUNCER should get the pity (lowest NATURAL PROBABILITY at ~52.9%)
      // Natural probs: ID=58.8%, EGO=92.7%, Announcer=52.9%
      // Old buggy code gave pity to EGO (lowest RATE 1.3%), but that wastes it!
      const announcerResult = result.targetResults.find(r => r.target.type === 'announcer')
      expect(announcerResult?.pityApplies).toBe(true)

      // Other targets should NOT have pity applied
      const idResult = result.targetResults.find(r => r.target.type === 'threeStarId')
      expect(idResult?.pityApplies).toBe(false)

      const egoResult = result.targetResults.find(r => r.target.type === 'ego')
      expect(egoResult?.pityApplies).toBe(false)

      // allTargetProbability should NOT be inflated by duplicate pity
      // With pity to Announcer (FIXED allocation):
      // - Announcer with pity: P(at least 1 from 2) ≈ 92.5%
      // - EGO natural: 92.7%
      // - ID natural (want 2): 58.8%
      // Expected: 92.5% × 92.7% × 58.8% ≈ 50.4%
      expect(result.allTargetProbability).toBeGreaterThan(0.48)
      expect(result.allTargetProbability).toBeLessThan(0.53)
    })
  })
})

describe('calculateCategoryDistribution', () => {
  describe('EGO (binomial distribution - 비복원추출)', () => {
    it('returns [1] for 0 wanted items', () => {
      const dist = calculateCategoryDistribution(100, 'ego', 0, 1, false)
      expect(dist).toEqual([1])
    })

    it('returns [1] for 0 pulls', () => {
      const dist = calculateCategoryDistribution(0, 'ego', 1, 1, false)
      expect(dist).toEqual([1])
    })

    it('returns valid distribution for 1 EGO wanted', () => {
      const dist = calculateCategoryDistribution(100, 'ego', 1, 1, false)
      // dist = [P(0), P(1+)]
      expect(dist).toHaveLength(2)
      expect(dist[0] + dist[1]).toBeCloseTo(1, 5) // Sum to 1
      expect(dist[0]).toBeGreaterThan(0) // P(0) > 0
      expect(dist[1]).toBeGreaterThan(0) // P(1+) > 0
    })

    it('returns higher probability with allEgoCollected (doubled rate)', () => {
      const distStandard = calculateCategoryDistribution(100, 'ego', 1, 1, false)
      const distAllEgo = calculateCategoryDistribution(100, 'ego', 1, 1, true)
      // allEgoCollected doubles rate: 0.65% → 1.3%
      expect(distAllEgo[1]).toBeGreaterThan(distStandard[1])
    })

    it('returns distribution for multiple EGOs wanted', () => {
      const dist = calculateCategoryDistribution(200, 'ego', 3, 3, true)
      // dist = [P(0), P(1), P(2), P(3+)]
      expect(dist).toHaveLength(4)
      const sum = dist.reduce((a, b) => a + b, 0)
      expect(sum).toBeCloseTo(1, 5)
    })
  })

  describe('Identity (Coupon Collector - 복원추출)', () => {
    it('returns [1] for 0 wanted items', () => {
      const dist = calculateCategoryDistribution(100, 'threeStarId', 0, 2, false)
      expect(dist).toEqual([1])
    })

    it('returns valid distribution for single ID from 2 featured', () => {
      const dist = calculateCategoryDistribution(100, 'threeStarId', 1, 2, false)
      // dist = [P(0), P(1+)]
      expect(dist).toHaveLength(2)
      expect(dist[0] + dist[1]).toBeCloseTo(1, 5)
    })

    it('returns distribution for all 2 IDs from 2 featured', () => {
      const dist = calculateCategoryDistribution(100, 'threeStarId', 2, 2, false)
      // dist = [P(0), P(1), P(2+)]
      expect(dist).toHaveLength(3)
      const sum = dist.reduce((a, b) => a + b, 0)
      expect(sum).toBeCloseTo(1, 5)
      // P(2) should be less than P(1) for reasonable pulls
      expect(dist[2]).toBeLessThan(dist[1])
    })

    it('handles 3 IDs from 3 featured correctly', () => {
      const dist = calculateCategoryDistribution(200, 'threeStarId', 3, 3, false)
      expect(dist).toHaveLength(4) // [P(0), P(1), P(2), P(3+)]
      const sum = dist.reduce((a, b) => a + b, 0)
      expect(sum).toBeCloseTo(1, 5)
    })
  })

  describe('Announcer (Coupon Collector)', () => {
    it('returns valid distribution for single announcer', () => {
      const dist = calculateCategoryDistribution(100, 'announcer', 1, 1, false)
      expect(dist).toHaveLength(2)
      expect(dist[0] + dist[1]).toBeCloseTo(1, 5)
    })

    it('returns distribution for 2 announcers from 2 featured', () => {
      const dist = calculateCategoryDistribution(200, 'announcer', 2, 2, false)
      expect(dist).toHaveLength(3)
      const sum = dist.reduce((a, b) => a + b, 0)
      expect(sum).toBeCloseTo(1, 5)
    })
  })
})

describe('convolveDistributions', () => {
  it('convolves two simple distributions', () => {
    // [0.6, 0.4] ⊗ [0.5, 0.5]
    // P(0) = 0.6 * 0.5 = 0.3
    // P(1) = 0.6 * 0.5 + 0.4 * 0.5 = 0.5
    // P(2) = 0.4 * 0.5 = 0.2
    const dist1 = [0.6, 0.4]
    const dist2 = [0.5, 0.5]
    const result = convolveDistributions(dist1, dist2)

    expect(result).toHaveLength(3)
    expect(result[0]).toBeCloseTo(0.3, 5)
    expect(result[1]).toBeCloseTo(0.5, 5)
    expect(result[2]).toBeCloseTo(0.2, 5)
  })

  it('preserves probability sum (sum stays 1)', () => {
    const dist1 = [0.3, 0.5, 0.2]
    const dist2 = [0.4, 0.4, 0.2]
    const result = convolveDistributions(dist1, dist2)

    const sum = result.reduce((a, b) => a + b, 0)
    expect(sum).toBeCloseTo(1, 5)
  })

  it('handles single-element distribution (degenerate case)', () => {
    const dist1 = [1] // Always 0
    const dist2 = [0.3, 0.7] // 0 or 1
    const result = convolveDistributions(dist1, dist2)

    expect(result).toEqual([0.3, 0.7])
  })

  it('computes larger convolutions correctly', () => {
    // 3-element distributions
    const dist1 = [0.2, 0.5, 0.3]
    const dist2 = [0.1, 0.6, 0.3]
    const result = convolveDistributions(dist1, dist2)

    expect(result).toHaveLength(5) // 3 + 3 - 1
    const sum = result.reduce((a, b) => a + b, 0)
    expect(sum).toBeCloseTo(1, 5)

    // Verify specific values
    // P(0) = 0.2 * 0.1 = 0.02
    expect(result[0]).toBeCloseTo(0.02, 5)
    // P(4) = 0.3 * 0.3 = 0.09
    expect(result[4]).toBeCloseTo(0.09, 5)
  })

  it('handles asymmetric distributions', () => {
    const dist1 = [0.9, 0.1] // Mostly 0
    const dist2 = [0.1, 0.9] // Mostly 1
    const result = convolveDistributions(dist1, dist2)

    expect(result).toHaveLength(3)
    // P(0) = 0.9 * 0.1 = 0.09
    expect(result[0]).toBeCloseTo(0.09, 5)
    // P(1) = 0.9 * 0.9 + 0.1 * 0.1 = 0.82
    expect(result[1]).toBeCloseTo(0.82, 5)
    // P(2) = 0.1 * 0.9 = 0.09
    expect(result[2]).toBeCloseTo(0.09, 5)
  })
})

describe('calculateSuccessiveTargetProbabilities', () => {
  const makeFeaturedCounts = (id: number, ego: number, announcer: number) => ({
    threeStarId: id,
    ego,
    announcer,
  })

  describe('single target scenarios', () => {
    it('returns empty array for empty targets', () => {
      const result = calculateSuccessiveTargetProbabilities(
        [],
        100,
        makeFeaturedCounts(1, 1, 0),
        false,
        0
      )
      expect(result).toEqual([])
    })

    it('returns empty array for 0 wanted items', () => {
      const targets: ExtractionTarget[] = [
        { type: 'threeStarId', wantedCopies: 0, currentCopies: 0 },
      ]
      const result = calculateSuccessiveTargetProbabilities(
        targets,
        100,
        makeFeaturedCounts(1, 0, 0),
        false,
        0
      )
      expect(result).toEqual([])
    })

    it('returns single entry for 1 target wanting 1 item', () => {
      const targets: ExtractionTarget[] = [
        { type: 'threeStarId', wantedCopies: 1, currentCopies: 0 },
      ]
      const result = calculateSuccessiveTargetProbabilities(
        targets,
        100,
        makeFeaturedCounts(1, 0, 0),
        false,
        0
      )

      expect(result).toHaveLength(1)
      expect(result[0].count).toBe(1)
      expect(result[0].probability).toBeGreaterThan(0)
      expect(result[0].probability).toBeLessThanOrEqual(1)
    })

    it('returns entries from n down to 1', () => {
      const targets: ExtractionTarget[] = [
        { type: 'threeStarId', wantedCopies: 3, currentCopies: 0 },
      ]
      const result = calculateSuccessiveTargetProbabilities(
        targets,
        100,
        makeFeaturedCounts(3, 0, 0),
        false,
        0
      )

      expect(result).toHaveLength(3)
      expect(result[0].count).toBe(3) // P(3+)
      expect(result[1].count).toBe(2) // P(2+)
      expect(result[2].count).toBe(1) // P(1+)
      // P(k+) should increase as k decreases
      expect(result[0].probability).toBeLessThanOrEqual(result[1].probability)
      expect(result[1].probability).toBeLessThanOrEqual(result[2].probability)
    })
  })

  describe('multiple targets (convolution)', () => {
    it('correctly combines 2 targets', () => {
      const targets: ExtractionTarget[] = [
        { type: 'threeStarId', wantedCopies: 2, currentCopies: 0 },
        { type: 'ego', wantedCopies: 1, currentCopies: 0 },
      ]
      const result = calculateSuccessiveTargetProbabilities(
        targets,
        100,
        makeFeaturedCounts(2, 1, 0),
        false,
        0
      )

      // Total items = 2 + 1 = 3
      expect(result).toHaveLength(3)
      expect(result[0].count).toBe(3) // P(3+)
      expect(result[1].count).toBe(2) // P(2+)
      expect(result[2].count).toBe(1) // P(1+)

      // Probabilities should be monotonically increasing as count decreases
      for (let i = 0; i < result.length - 1; i++) {
        expect(result[i].probability).toBeLessThanOrEqual(result[i + 1].probability)
      }
    })

    it('correctly combines 3 targets (ID + EGO + Announcer)', () => {
      const targets: ExtractionTarget[] = [
        { type: 'threeStarId', wantedCopies: 2, currentCopies: 0 },
        { type: 'ego', wantedCopies: 1, currentCopies: 0 },
        { type: 'announcer', wantedCopies: 2, currentCopies: 0 },
      ]
      const result = calculateSuccessiveTargetProbabilities(
        targets,
        200,
        makeFeaturedCounts(2, 1, 2),
        false,
        0
      )

      // Total items = 2 + 1 + 2 = 5
      expect(result).toHaveLength(5)
      expect(result[0].count).toBe(5) // P(5+)
      expect(result[4].count).toBe(1) // P(1+)
    })
  })

  describe('pity application', () => {
    it('applies pity correctly (pity reduces natural requirement)', () => {
      const targets: ExtractionTarget[] = [
        { type: 'threeStarId', wantedCopies: 2, currentCopies: 0 },
      ]

      // Without pity
      const noPity = calculateSuccessiveTargetProbabilities(
        targets,
        100,
        makeFeaturedCounts(2, 0, 0),
        false,
        0
      )

      // With 1 pity
      const withPity = calculateSuccessiveTargetProbabilities(
        targets,
        100,
        makeFeaturedCounts(2, 0, 0),
        false,
        1
      )

      // P(2+) with pity should be >= P(2+) without pity
      expect(withPity[0].probability).toBeGreaterThanOrEqual(noPity[0].probability)
      // P(1+) with pity should be >= P(1+) without pity
      expect(withPity[1].probability).toBeGreaterThanOrEqual(noPity[1].probability)
    })

    it('returns 100% when pity covers all items', () => {
      const targets: ExtractionTarget[] = [
        { type: 'ego', wantedCopies: 2, currentCopies: 0 },
      ]
      const result = calculateSuccessiveTargetProbabilities(
        targets,
        100,
        makeFeaturedCounts(0, 2, 0),
        true,
        3 // 3 pity covers 2 wanted
      )

      expect(result).toHaveLength(2)
      expect(result[0].probability).toBe(1) // P(2+) = 100%
      expect(result[1].probability).toBe(1) // P(1+) = 100%
    })

    it('handles partial pity coverage', () => {
      const targets: ExtractionTarget[] = [
        { type: 'threeStarId', wantedCopies: 3, currentCopies: 0 },
      ]
      // 1 pity for 3 wanted = need 2 naturally
      const result = calculateSuccessiveTargetProbabilities(
        targets,
        200,
        makeFeaturedCounts(3, 0, 0),
        false,
        1
      )

      expect(result).toHaveLength(3)
      // P(3+) with 1 pity = P(need 2+ naturally)
      // P(2+) with 1 pity = P(need 1+ naturally)
      // P(1+) with 1 pity = P(need 0+ naturally) = 100%
      expect(result[2].probability).toBe(1)
    })
  })

  describe('edge cases', () => {
    it('handles 0 pulls with pity', () => {
      const targets: ExtractionTarget[] = [
        { type: 'ego', wantedCopies: 1, currentCopies: 0 },
      ]
      // 0 pulls but have pity
      const result = calculateSuccessiveTargetProbabilities(
        targets,
        0,
        makeFeaturedCounts(0, 1, 0),
        false,
        1
      )

      expect(result).toHaveLength(1)
      expect(result[0].probability).toBe(1) // Pity covers it
    })

    it('handles target with currentCopies partially fulfilled', () => {
      const targets: ExtractionTarget[] = [
        { type: 'threeStarId', wantedCopies: 3, currentCopies: 2 },
      ]
      // Only need 1 more
      const result = calculateSuccessiveTargetProbabilities(
        targets,
        100,
        makeFeaturedCounts(3, 0, 0),
        false,
        0
      )

      expect(result).toHaveLength(1) // Only 1 item needed
      expect(result[0].count).toBe(1)
    })
  })
})

describe('calculateExtraction - successiveProbabilities and totalItemsWanted', () => {
  describe('totalItemsWanted', () => {
    it('calculates total items correctly for single target', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 2,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 2, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.totalItemsWanted).toBe(2)
    })

    it('calculates total items correctly for multiple targets', () => {
      const input: ExtractionInput = {
        plannedPulls: 200,
        featuredThreeStarCount: 2,
        featuredEgoCount: 1,
        featuredAnnouncerCount: 2,
        modifiers: { allEgoCollected: false, hasAnnouncer: true },
        targets: [
          { type: 'threeStarId', wantedCopies: 2, currentCopies: 0 },
          { type: 'ego', wantedCopies: 1, currentCopies: 0 },
          { type: 'announcer', wantedCopies: 2, currentCopies: 0 },
        ],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.totalItemsWanted).toBe(5) // 2 + 1 + 2
    })

    it('subtracts currentCopies from totalItemsWanted', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 2,
        featuredEgoCount: 1,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [
          { type: 'threeStarId', wantedCopies: 2, currentCopies: 1 },
          { type: 'ego', wantedCopies: 1, currentCopies: 0 },
        ],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.totalItemsWanted).toBe(2) // (2-1) + (1-0) = 2
    })

    it('returns 0 for empty targets', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.totalItemsWanted).toBe(0)
    })
  })

  describe('successiveProbabilities', () => {
    it('returns empty array for no targets', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.successiveProbabilities).toEqual([])
    })

    it('returns correct structure for single target', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 1,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 1, currentCopies: 0 }],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.successiveProbabilities).toHaveLength(1)
      expect(result.successiveProbabilities[0]).toHaveProperty('count', 1)
      expect(result.successiveProbabilities[0]).toHaveProperty('probability')
    })

    it('returns entries ordered from n down to 1', () => {
      const input: ExtractionInput = {
        plannedPulls: 200,
        featuredThreeStarCount: 2,
        featuredEgoCount: 1,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [
          { type: 'threeStarId', wantedCopies: 2, currentCopies: 0 },
          { type: 'ego', wantedCopies: 1, currentCopies: 0 },
        ],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      expect(result.successiveProbabilities).toHaveLength(3)
      expect(result.successiveProbabilities[0].count).toBe(3) // P(all 3)
      expect(result.successiveProbabilities[1].count).toBe(2) // P(2+)
      expect(result.successiveProbabilities[2].count).toBe(1) // P(1+)
    })

    it('probabilities are monotonically increasing as count decreases', () => {
      const input: ExtractionInput = {
        plannedPulls: 100,
        featuredThreeStarCount: 3,
        featuredEgoCount: 2,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [
          { type: 'threeStarId', wantedCopies: 3, currentCopies: 0 },
          { type: 'ego', wantedCopies: 2, currentCopies: 0 },
        ],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      // Total = 5, so we have P(5), P(4), P(3), P(2), P(1)
      expect(result.successiveProbabilities).toHaveLength(5)

      for (let i = 0; i < result.successiveProbabilities.length - 1; i++) {
        expect(result.successiveProbabilities[i].probability)
          .toBeLessThanOrEqual(result.successiveProbabilities[i + 1].probability)
      }
    })

    it('applies pity correctly to successive probabilities', () => {
      const baseInput: ExtractionInput = {
        plannedPulls: 200,
        featuredThreeStarCount: 2,
        featuredEgoCount: 0,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [{ type: 'threeStarId', wantedCopies: 2, currentCopies: 0 }],
        currentPity: 0,
      }

      const noPityResult = calculateExtraction({
        ...baseInput,
        plannedPulls: 100, // No pity (< 200)
      })

      const withPityResult = calculateExtraction({
        ...baseInput,
        plannedPulls: 200, // 1 pity trigger
      })

      // With pity, probabilities should be higher
      expect(withPityResult.pityCount).toBe(1)
      expect(withPityResult.successiveProbabilities[0].probability)
        .toBeGreaterThanOrEqual(noPityResult.successiveProbabilities[0].probability)
    })

    it('P(all) in successiveProbabilities uses convolution (different from per-target allTargetProbability)', () => {
      // Note: successiveProbabilities uses convolution (joint distribution model)
      // while allTargetProbability uses per-target independence (multiplication)
      // These differ because:
      // - Convolution: P(X+Y >= n) considers all ways to reach n items
      // - Independence: P(A and B) = P(A) * P(B) for each category
      const input: ExtractionInput = {
        plannedPulls: 200,
        featuredThreeStarCount: 2,
        featuredEgoCount: 1,
        featuredAnnouncerCount: 0,
        modifiers: { allEgoCollected: false, hasAnnouncer: false },
        targets: [
          { type: 'threeStarId', wantedCopies: 2, currentCopies: 0 },
          { type: 'ego', wantedCopies: 1, currentCopies: 0 },
        ],
        currentPity: 0,
      }

      const result = calculateExtraction(input)
      const pAllFromSuccessive = result.successiveProbabilities[0].probability

      // Both should be valid probabilities in [0, 1]
      expect(pAllFromSuccessive).toBeGreaterThanOrEqual(0)
      expect(pAllFromSuccessive).toBeLessThanOrEqual(1)
      expect(result.allTargetProbability).toBeGreaterThanOrEqual(0)
      expect(result.allTargetProbability).toBeLessThanOrEqual(1)

      // Convolution result is typically >= per-target result because it considers
      // getting "all n items" through any combination, not specific items per category
      expect(pAllFromSuccessive).toBeGreaterThanOrEqual(result.allTargetProbability * 0.9)
    })
  })

  describe('pity allocation with allEgoCollected modifier (bug fix)', () => {
    it('2/1/1 @ 200 pulls: allEgoCollected should increase probability by allocating pity optimally', () => {
      // Bug scenario: When allEgoCollected doubles EGO rate (0.65% → 1.3%),
      // pity should NOT go to EGO anymore (which has decent 92.7% natural prob).
      // Instead, pity should go to the target with LOWEST natural probability.
      //
      // For 2/1/1 wanting 1 of each:
      // - ID (want 1 from 2): P ≈ 94.6% natural
      // - EGO (want 1 from 1): P ≈ 72.5% (false) or 92.7% (true) natural
      // - Announcer (want 1 from 1): P ≈ 92.7% natural
      //
      // With allEgoCollected=false: Pity → EGO (lowest at 72.5%)
      // With allEgoCollected=true: Pity should → Announcer or EGO (both 92.7%)
      //   But old buggy code gave pity to ID (lowest RATE 0.725% but highest PROBABILITY 94.6%)
      //   This wasted the doubled EGO rate bonus!

      const baseInput = {
        plannedPulls: 200,
        featuredThreeStarCount: 2,
        featuredEgoCount: 1,
        featuredAnnouncerCount: 1,
        targets: [
          { type: 'threeStarId' as const, wantedCopies: 1, currentCopies: 0 },
          { type: 'ego' as const, wantedCopies: 1, currentCopies: 0 },
          { type: 'announcer' as const, wantedCopies: 1, currentCopies: 0 },
        ],
        currentPity: 0,
      }

      const resultFalse = calculateExtraction({
        ...baseInput,
        modifiers: { allEgoCollected: false, hasAnnouncer: true },
      })

      const resultTrue = calculateExtraction({
        ...baseInput,
        modifiers: { allEgoCollected: true, hasAnnouncer: true },
      })

      // Both should have 1 pity
      expect(resultFalse.pityCount).toBe(1)
      expect(resultTrue.pityCount).toBe(1)

      // With allEgoCollected=false: probability ≈ 87.65%
      // - Pity → EGO (100%)
      // - ID natural ≈ 94.6%
      // - Announcer natural ≈ 92.7%
      // - Total: 0.946 × 1.0 × 0.927 ≈ 0.8765
      expect(resultFalse.allTargetProbability).toBeCloseTo(0.8765, 2)

      // With allEgoCollected=true: probability should ALSO be ≈ 87.65%
      // - EGO rate doubled to 1.3%, but EGO and Announcer now have same natural prob (92.7%)
      // - Pity can go to either → doesn't matter which
      // - If pity → EGO: 0.946 × 1.0 × 0.927 ≈ 0.8765
      // - If pity → Announcer: 0.946 × 0.927 × 1.0 ≈ 0.8765
      expect(resultTrue.allTargetProbability).toBeCloseTo(0.8765, 2)

      // The key test: probabilities should be nearly identical
      // (slight difference due to rounding/floating point)
      expect(Math.abs(resultTrue.allTargetProbability - resultFalse.allTargetProbability)).toBeLessThan(0.001)
    })

    it('2/1/1 @ 200 pulls with wanting 2 IDs: allEgoCollected should SIGNIFICANTLY increase probability', () => {
      // This test exposes the BUG more clearly:
      // When wanting 2 IDs (collect BOTH), ID has LOWEST natural probability.
      // With allEgoCollected=true, pity should go to ID (worst at 58.8%),
      // NOT to EGO (which improved from 72.5% to 92.7%).

      const baseInput = {
        plannedPulls: 200,
        featuredThreeStarCount: 2,
        featuredEgoCount: 1,
        featuredAnnouncerCount: 1,
        targets: [
          { type: 'threeStarId' as const, wantedCopies: 2, currentCopies: 0 }, // Want BOTH IDs
          { type: 'ego' as const, wantedCopies: 1, currentCopies: 0 },
          { type: 'announcer' as const, wantedCopies: 1, currentCopies: 0 },
        ],
        currentPity: 0,
      }

      const resultFalse = calculateExtraction({
        ...baseInput,
        modifiers: { allEgoCollected: false, hasAnnouncer: true },
      })

      const resultTrue = calculateExtraction({
        ...baseInput,
        modifiers: { allEgoCollected: true, hasAnnouncer: true },
      })

      // Both should have 1 pity
      expect(resultFalse.pityCount).toBe(1)
      expect(resultTrue.pityCount).toBe(1)

      // With allEgoCollected=false (AFTER FIX):
      // - ID natural (want 2): 0.767^2 ≈ 58.8% (LOWEST!)
      // - EGO natural: 72.5%
      // - Announcer natural: 92.7%
      // - Pity → ID (lowest natural prob, even though EGO has lowest RATE)
      // - ID with pity: P(at least 1 from 2) ≈ 94.6%
      // - Total: 0.946 × 0.725 × 0.927 ≈ 0.636
      expect(resultFalse.allTargetProbability).toBeCloseTo(0.636, 2)

      // With allEgoCollected=true (FIXED):
      // - ID natural (want 2): 0.767^2 ≈ 58.8% (LOWEST!)
      // - EGO natural: 92.7% (doubled rate)
      // - Announcer natural: 92.7%
      // - Pity should → ID (lowest at 58.8%)
      // - Total: (0.946 with 1 pity) × 0.927 × 0.927 ≈ 0.813
      expect(resultTrue.allTargetProbability).toBeCloseTo(0.813, 2)

      // The CRITICAL test: allEgoCollected=true should be SIGNIFICANTLY better
      // After fix: 81.3% vs 63.6% (17.7% absolute increase!)
      // Before fix: both were ~54.5% (modifier had no effect - BUG!)
      expect(resultTrue.allTargetProbability).toBeGreaterThan(resultFalse.allTargetProbability + 0.15)
    })
  })
})
