import { describe, it, expect } from 'vitest'

import { pctStyle } from '../rect'

describe('pctStyle', () => {
  it('positions the box absolutely in percentages of its ancestor', () => {
    expect(pctStyle({ left: 12.5, top: 3, width: 75, height: 20.25 })).toEqual({
      position: 'absolute',
      left: '12.5%',
      top: '3%',
      width: '75%',
      height: '20.25%',
      maxWidth: 'none',
    })
  })

  it('keeps a zero edge as a percentage rather than dropping it', () => {
    expect(pctStyle({ left: 0, top: 0, width: 100, height: 100 })).toEqual({
      position: 'absolute',
      left: '0%',
      top: '0%',
      width: '100%',
      height: '100%',
      maxWidth: 'none',
    })
  })

  it('lets a rect overhang its ancestor, which the preflight img cap would clamp', () => {
    expect(pctStyle({ left: -7.161, top: 4.667, width: 109.161, height: 93.778 }).maxWidth).toBe(
      'none',
    )
  })
})
