import { describe, expect, it } from 'vitest'

import {
  PANEL_COIN_NUMBER_SHADOW,
  PANEL_LEVEL_SHADOW,
  emUnderlayShadow,
  panelNameShadow,
} from '../panelUnderlay'

function layers(shadow: string | undefined): string[] {
  return (shadow ?? '').split(', ')
}

function offsetsInUnits(shadow: string | undefined, fontUnits: number): [number, number][] {
  return layers(shadow).map((each) => {
    const [x, y] = each.split(' ').map((token) => Number.parseFloat(token))
    return [Number(((x ?? 0) * fontUnits).toFixed(2)), Number(((y ?? 0) * fontUnits).toFixed(2))]
  })
}

describe('panel name underlay', () => {
  it('offsets a 45-unit Korean skill name 2.12 units right and down with a 0.74-unit ring', () => {
    const offsets = offsetsInUnits(panelNameShadow('KR'), 45)
    expect(offsets[0]).toEqual([2.12, 2.12])
    expect(offsets).toContainEqual([2.86, 2.12])
    expect(offsets).toContainEqual([2.12, 1.38])
    expect(offsets).toHaveLength(9)
  })

  it('offsets a 40-unit Korean passive name 1.89 units with a 0.66-unit ring', () => {
    const offsets = offsetsInUnits(panelNameShadow('KR'), 40)
    expect(offsets[0]).toEqual([1.88, 1.88])
    expect(offsets).toContainEqual([2.54, 1.88])
  })

  it('draws the English and Japanese names without a ring at their own offsets', () => {
    expect(offsetsInUnits(panelNameShadow('EN'), 45)).toEqual([[2.06, 2.06]])
    expect(offsetsInUnits(panelNameShadow('JP'), 45)).toEqual([[1.83, 1.61]])
  })

  it('draws every layer hard in #040001', () => {
    for (const each of layers(panelNameShadow('KR'))) expect(each).toMatch(/em 0 #040001$/)
  })

  it('draws the Chinese name with the Korean underlay', () => {
    expect(panelNameShadow('CN')).toBe(panelNameShadow('KR'))
  })
})

describe('panel number underlays', () => {
  it('rings the 60-unit coin scale 1.14 units out in black without an offset', () => {
    const offsets = offsetsInUnits(PANEL_COIN_NUMBER_SHADOW, 60)
    expect(offsets[0]).toEqual([0, 0])
    expect(offsets).toContainEqual([1.14, 0])
    expect(offsets).toContainEqual([0, -1.14])
    expect(PANEL_COIN_NUMBER_SHADOW).toMatch(/#000000$/)
  })

  it('rings the 102.54-unit base power 1.95 units out', () => {
    expect(offsetsInUnits(PANEL_COIN_NUMBER_SHADOW, 102.54)).toContainEqual([1.95, 0])
  })

  it('offsets the 75-unit level 2.62 units right and down', () => {
    expect(offsetsInUnits(PANEL_LEVEL_SHADOW, 75)).toEqual([[2.62, 2.62]])
  })

  it('draws a single layer when the underlay has no dilate', () => {
    expect(emUnderlayShadow({ dx: 0.1, dy: 0.2, dilate: 0 }, '#040001')).toBe(
      '0.1em 0.2em 0 #040001',
    )
  })
})
