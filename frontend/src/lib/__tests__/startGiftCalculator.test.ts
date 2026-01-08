/**
 * startGiftCalculator.test.ts
 *
 * Unit tests for start gift selection calculator function.
 * Tests base calculation, buff effects, and edge cases.
 */

import { describe, it, expect, vi } from 'vitest'
import { calculateMaxGiftSelection } from '../startGiftCalculator'
import type { StartBuff } from '@/types/StartBuffTypes'

// Mock getBuffById to return buffs from array
vi.mock('@/hooks/useStartBuffData', () => ({
  getBuffById: (buffs: StartBuff[] | undefined, id: number) =>
    buffs?.find((b) => b.id === String(id)),
}))

describe('calculateMaxGiftSelection', () => {
  const mockBuffs: StartBuff[] = [
    {
      id: '100',
      baseId: 100,
      level: 1,
      name: 'Base Buff',
      cost: 0,
      effects: [],
      iconSpriteId: 'icon100',
    },
    {
      id: '200',
      baseId: 200,
      level: 1,
      name: 'Gift Select +1',
      cost: 10,
      effects: [{ type: 'ADDITIONAL_START_EGO_GIFT_SELECT', value: 1, isTypoExist: false }],
      iconSpriteId: 'icon200',
    },
    {
      id: '300',
      baseId: 300,
      level: 1,
      name: 'Gift Select +2',
      cost: 20,
      effects: [{ type: 'ADDITIONAL_START_EGO_GIFT_SELECT', value: 2, isTypoExist: false }],
      iconSpriteId: 'icon300',
    },
    {
      id: '400',
      baseId: 400,
      level: 1,
      name: 'Other Effect',
      cost: 5,
      effects: [{ type: 'SOME_OTHER_EFFECT', value: 10, isTypoExist: false }],
      iconSpriteId: 'icon400',
    },
  ]

  describe('base cases', () => {
    it('returns 1 when buffs is undefined', () => {
      const result = calculateMaxGiftSelection(undefined, new Set([200]))
      expect(result).toBe(1)
    })

    it('returns 1 when buffs is empty array', () => {
      const result = calculateMaxGiftSelection([], new Set([200]))
      expect(result).toBe(1)
    })

    it('returns 1 when no buffs are selected', () => {
      const result = calculateMaxGiftSelection(mockBuffs, new Set())
      expect(result).toBe(1)
    })

    it('returns 1 when selected buff has no ADDITIONAL_START_EGO_GIFT_SELECT effect', () => {
      const result = calculateMaxGiftSelection(mockBuffs, new Set([100]))
      expect(result).toBe(1)
    })
  })

  describe('single buff effects', () => {
    it('returns 2 when buff with +1 gift select is selected', () => {
      const result = calculateMaxGiftSelection(mockBuffs, new Set([200]))
      expect(result).toBe(2)
    })

    it('returns 3 when buff with +2 gift select is selected', () => {
      const result = calculateMaxGiftSelection(mockBuffs, new Set([300]))
      expect(result).toBe(3)
    })
  })

  describe('multiple buff effects', () => {
    it('sums up multiple ADDITIONAL_START_EGO_GIFT_SELECT effects', () => {
      // +1 from buff 200 + +2 from buff 300 = 3 additional, total 4
      const result = calculateMaxGiftSelection(mockBuffs, new Set([200, 300]))
      expect(result).toBe(4)
    })

    it('ignores buffs without ADDITIONAL_START_EGO_GIFT_SELECT effect', () => {
      // Only +1 from buff 200, buff 400 has different effect type
      const result = calculateMaxGiftSelection(mockBuffs, new Set([200, 400]))
      expect(result).toBe(2)
    })

    it('handles mix of buffs with and without the effect', () => {
      // +1 from buff 200, buff 100 has no effects, buff 400 has other effect
      const result = calculateMaxGiftSelection(mockBuffs, new Set([100, 200, 400]))
      expect(result).toBe(2)
    })
  })

  describe('edge cases', () => {
    it('ignores selected buff IDs that do not exist in buffs array', () => {
      const result = calculateMaxGiftSelection(mockBuffs, new Set([999]))
      expect(result).toBe(1)
    })

    it('handles effect with value of 0', () => {
      const buffsWithZeroValue: StartBuff[] = [
        {
          id: '500',
          baseId: 500,
          level: 1,
          name: 'Zero Effect',
          cost: 0,
          effects: [{ type: 'ADDITIONAL_START_EGO_GIFT_SELECT', value: 0, isTypoExist: false }],
          iconSpriteId: 'icon500',
        },
      ]
      const result = calculateMaxGiftSelection(buffsWithZeroValue, new Set([500]))
      expect(result).toBe(1) // 0 is falsy, so it doesn't add
    })

    it('handles effect with undefined value', () => {
      const buffsWithUndefinedValue: StartBuff[] = [
        {
          id: '600',
          baseId: 600,
          level: 1,
          name: 'Undefined Effect',
          cost: 0,
          effects: [{ type: 'ADDITIONAL_START_EGO_GIFT_SELECT', isTypoExist: false }],
          iconSpriteId: 'icon600',
        },
      ]
      const result = calculateMaxGiftSelection(buffsWithUndefinedValue, new Set([600]))
      expect(result).toBe(1)
    })
  })
})
