import { describe, it, expect } from 'vitest'
import attributeColorCode from '@static/data/color/attributeColorCode.json'
import seasonColorCode from '@static/data/color/seasonColorCode.json'
import { getAttributeColors, getSeasonColor } from '../colorUtils'
import { darkenColor } from '@/lib/colorUtils'

describe('getAttributeColors', () => {
  it('paints an attribute with its type role', () => {
    expect(getAttributeColors('CRIMSON').primary).toBe(attributeColorCode.CRIMSON.type)
    expect(getAttributeColors('NEUTRAL').primary).toBe(attributeColorCode.NEUTRAL.type)
  })

  it('darkens the type color to the plate ramp floor', () => {
    const { primary, dark } = getAttributeColors('AZURE')
    expect(dark).toBe(darkenColor(primary, 0.67))
  })

  it('returns the fallback pair for a missing, unknown, or typeless attribute', () => {
    expect(getAttributeColors()).toEqual(getAttributeColors('azure'))
    expect(getAttributeColors('NONE')).toEqual(getAttributeColors())
  })
})

describe('getSeasonColor', () => {
  it('reads numbered seasons and the collaboration code by key', () => {
    expect(getSeasonColor(3)).toBe(seasonColorCode['3'])
    expect(getSeasonColor(8000)).toBe(seasonColorCode['8000'])
  })

  it('maps every Walpurgisnacht code onto the shared entry', () => {
    expect(getSeasonColor(9101)).toBe(seasonColorCode['9100'])
    expect(getSeasonColor(9109)).toBe(seasonColorCode['9100'])
  })

  it('returns undefined for the standard season', () => {
    expect(getSeasonColor(0)).toBeUndefined()
  })
})
