import { describe, it, expect } from 'vitest'

import { RESIST_LEVEL_COLOURS, resistLevel } from '../resistance'

describe('resistLevel', () => {
  it.each([
    [0, 'immune'],
    [0.1, 'ineffective'],
    [0.2, 'ineffective'],
    [0.4, 'ineffective'],
    [0.5, 'ineffective'],
    [0.51, 'ineffective'],
    [0.6, 'endure'],
    [0.7, 'endure'],
    [0.75, 'endure'],
    [0.8, 'endure'],
    [0.9, 'endure'],
    [1, 'normal'],
    [1.1, 'weak'],
    [1.2, 'weak'],
    [1.25, 'weak'],
    [1.3, 'weak'],
    [1.5, 'weak'],
    [1.6, 'fatal'],
    [1.75, 'fatal'],
    [1.8, 'fatal'],
    [2, 'fatal'],
  ])('classifies %f as %s by the client cuts', (value, level) => {
    expect(resistLevel(value)).toBe(level)
  })

  it('takes the client colour for each level and for a blank', () => {
    expect(RESIST_LEVEL_COLOURS).toEqual({
      immune: '#808080',
      ineffective: '#808080',
      endure: '#CCCCCC',
      normal: '#E4C393',
      weak: '#FF8D8D',
      fatal: '#FF4040',
      blank: '#E4C393',
    })
  })
})
