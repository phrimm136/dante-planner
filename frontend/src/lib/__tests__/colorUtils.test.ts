import { describe, it, expect } from 'vitest'
import { darkenColor } from '../colorUtils'

describe('darkenColor', () => {
  it('scales each channel toward black', () => {
    expect(darkenColor('#ff8000', 0.5)).toBe('#804000')
  })

  it('drops an alpha channel and darkens the rgb part', () => {
    expect(darkenColor('#ff80005a', 0.5)).toBe('#804000')
  })
})
