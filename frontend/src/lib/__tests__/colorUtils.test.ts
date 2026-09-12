import { describe, it, expect } from 'vitest'
import { darkenColor, withAlpha } from '../colorUtils'

describe('darkenColor', () => {
  it('scales each channel toward black', () => {
    expect(darkenColor('#ff8000', 0.5)).toBe('#804000')
  })

  it('drops an alpha channel and darkens the rgb part', () => {
    expect(darkenColor('#ff80005a', 0.5)).toBe('#804000')
  })
})

describe('withAlpha', () => {
  it('appends the alpha channel to a 6-digit color', () => {
    expect(withAlpha('#ff8000', 0.5)).toBe('#ff800080')
  })

  it('replaces the alpha channel of an 8-digit color', () => {
    expect(withAlpha('#ff8000ff', 0.75)).toBe('#ff8000bf')
  })
})
