import { describe, it, expect } from 'vitest'

import { aspectOf } from '../geometry'

describe('aspectOf', () => {
  it('reads a card box as its width over its height', () => {
    expect(aspectOf({ widthPx: 160, heightPx: 232 })).toBe(160 / 232)
  })

  it('reads a square box as one', () => {
    expect(aspectOf({ widthPx: 96, heightPx: 96 })).toBe(1)
  })
})
