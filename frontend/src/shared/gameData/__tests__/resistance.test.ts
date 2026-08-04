import { describe, it, expect } from 'vitest'

import { getResistanceInfo } from '../resistance'

describe('getResistanceInfo', () => {
  it.each([
    [Number.POSITIVE_INFINITY, 'fatal'],
    [10, 'fatal'],
    [2.5, 'fatal'],
    [2.0, 'fatal'],
    [1.6, 'fatal'],
    [1.500001, 'fatal'],
    [1.5, 'weak'],
    [1.1, 'weak'],
    [1.000001, 'weak'],
    [1.0, 'normal'],
    [0.999999, 'endure'],
    [0.9, 'endure'],
    [0.75, 'endure'],
    [0.749999, 'ineffective'],
    [0.74, 'ineffective'],
    [0, 'ineffective'],
    [-0.01, 'ineffective'],
    [-5, 'ineffective'],
    [Number.NEGATIVE_INFINITY, 'ineffective'],
    [Number.NaN, 'ineffective'],
  ])('classifies %f as %s', (value, categoryKey) => {
    expect(getResistanceInfo(value).categoryKey).toBe(categoryKey)
  })

  it('gives every band its own colour', () => {
    const colors = [2.5, 1.2, 1.0, 0.8, 0.5].map((value) => getResistanceInfo(value).color)
    expect(new Set(colors).size).toBe(colors.length)
  })

  it('echoes the input value back', () => {
    expect(getResistanceInfo(1.25).value).toBe(1.25)
  })
})
