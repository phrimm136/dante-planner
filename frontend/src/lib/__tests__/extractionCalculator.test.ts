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
  })
})
