import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import { IMPORTANCE_DECORATION } from '@/lib/constants'
import {
  hasImportance,
  importanceNameStyle,
  importanceStyle,
  stripeTileWidth,
  toImportanceTier,
} from '../importanceTable'

const CAUTION_STRIPE = resolve(
  import.meta.dirname,
  '../../../../../../static/images/UI/common/caution-stripe.webp',
)

function webpCanvasSize(file: string): { width: number; height: number } {
  const bytes = readFileSync(file)
  expect(bytes.toString('ascii', 12, 16)).toBe('VP8X')
  return { width: bytes.readUIntLE(24, 3) + 1, height: bytes.readUIntLE(27, 3) + 1 }
}

describe('importance tiers', () => {
  it.each([0, 4, -1])('treats level %i as undecorated', (level) => {
    expect(toImportanceTier(level)).toBeUndefined()
    expect(hasImportance(level)).toBe(false)
    expect(importanceStyle(level, 'enemy')).toBeUndefined()
    expect(importanceNameStyle(level, 'enemy')).toBeUndefined()
  })

  it.each([
    [1, 0.2, false, 3.0, [0.3, 0.4]],
    [2, 0.4, true, 2.0, [0.4, 0.6]],
    [3, 0.5, true, 0.8, [0.5, 0.8]],
  ])(
    'level %i: icon alpha %f, glow %s, flicker %fs, blink %j',
    (level, alpha, glow, flicker, blink) => {
      const style = importanceStyle(level, 'player')
      expect(style?.iconAlpha).toBe(alpha)
      expect(style?.hasGlow).toBe(glow)
      expect(style?.flickerPeriodS).toBe(flicker)
      expect(style?.stripeBlinkAlpha).toEqual(blink)
    },
  )
})

describe('importance palettes', () => {
  it('reads enemy and player colors from the client table', () => {
    expect(importanceStyle(3, 'enemy')?.colors).toEqual(importanceColorCode.enemy['3'])
    expect(importanceStyle(3, 'player')?.colors).toEqual(importanceColorCode.player['3'])
    expect(importanceStyle(3, 'enemy')?.colors.stripe).not.toBe(
      importanceStyle(3, 'player')?.colors.stripe,
    )
  })

  it('colors the level 3 player passive with panel #87CB00 and name #F6FF00', () => {
    const colors = importanceStyle(3, 'player')?.colors
    expect(colors?.passivePanel).toMatch(/^#87cb00(ff)?$/i)
    expect(colors?.passiveName).toMatch(/^#f6ff00(ff)?$/i)
  })
})

describe('stripe tile', () => {
  it('matches the caution texture pixel size', () => {
    expect(webpCanvasSize(CAUTION_STRIPE)).toEqual({
      width: IMPORTANCE_DECORATION.STRIPE_TILE.WIDTH,
      height: IMPORTANCE_DECORATION.STRIPE_TILE.HEIGHT,
    })
  })

  it('scales one tile width from the band height by 1102 / 42', () => {
    expect(stripeTileWidth()).toBe('calc(var(--band-h) * 1102 / 42)')
  })
})
