/**
 * egoGiftCardProps.test.ts
 *
 * Unit tests for the spec-to-card-props builder, including the defaults it
 * applies to spec entries that omit a list field.
 */

import { describe, it, expect } from 'vitest'

import { toEGOGiftCardProps } from '../egoGiftCardProps'
import type { EGOGiftSpec } from '../../types/EGOGiftTypes'

function makeSpec(overrides: Partial<EGOGiftSpec> = {}): EGOGiftSpec {
  return {
    tag: ['TIER_3', 'GIFT'],
    keyword: 'Burn',
    battleKeywordList: ['Poise'],
    attributeType: 'CRIMSON',
    themePack: ['1001'],
    maxEnhancement: 2,
    ...overrides,
  }
}

describe('toEGOGiftCardProps', () => {
  it('carries the spec fields the card reads onto the props', () => {
    expect(toEGOGiftCardProps('9001', makeSpec())).toEqual({
      id: '9001',
      tag: ['TIER_3', 'GIFT'],
      keyword: 'Burn',
      battleKeywordList: ['Poise'],
      attributeType: 'CRIMSON',
      themePack: ['1001'],
      maxEnhancement: 2,
    })
  })

  const defaultCases: {
    name: string
    spec: EGOGiftSpec
    field: keyof ReturnType<typeof toEGOGiftCardProps>
    expected: unknown
  }[] = [
    {
      name: 'defaults a missing battleKeywordList to empty',
      spec: makeSpec({ battleKeywordList: undefined as unknown as string[] }),
      field: 'battleKeywordList',
      expected: [],
    },
    {
      name: 'keeps an empty battleKeywordList empty',
      spec: makeSpec({ battleKeywordList: [] }),
      field: 'battleKeywordList',
      expected: [],
    },
    {
      name: 'keeps a null keyword null',
      spec: makeSpec({ keyword: null }),
      field: 'keyword',
      expected: null,
    },
    {
      name: 'keeps an empty theme pack list empty',
      spec: makeSpec({ themePack: [] }),
      field: 'themePack',
      expected: [],
    },
    {
      name: 'keeps a zero max enhancement',
      spec: makeSpec({ maxEnhancement: 0 }),
      field: 'maxEnhancement',
      expected: 0,
    },
  ]

  it.each(defaultCases)('$name', ({ spec, field, expected }) => {
    expect(toEGOGiftCardProps('9001', spec)[field]).toEqual(expected)
  })

  it('takes the id from the argument, not the spec', () => {
    expect(toEGOGiftCardProps('9002', makeSpec()).id).toBe('9002')
  })
})
