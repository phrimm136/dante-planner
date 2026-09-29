import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import { IMPORTANCE_DECORATION } from '@/lib/constants'
import {
  hasImportance,
  importanceBannerStyle,
  importanceGeometry,
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
    expect(importanceBannerStyle(level, 'enemy')).toBeUndefined()
  })

  it.each([
    [1, 0.2, false, 'icon', 3.0, [0.7, 1.0], [0.3, 0.4], [0, 0.3]],
    [2, 0.4, true, 'glow', 2.0, [0.7, 1.0], [0.4, 0.6], [0.2, 0.5]],
    [3, 0.5, true, 'glow', 0.8, [0.5, 1.0], [0.5, 0.8], [0.5, 1.0]],
  ])(
    'level %i: icon alpha %f, glow %s, flicker on the %s over %fs within %j, blink %j, outline %j',
    (level, alpha, glow, target, period, flicker, blink, outline) => {
      const style = importanceStyle(level, 'player')
      expect(style?.iconAlpha).toBe(alpha)
      expect(style?.hasGlow).toBe(glow)
      expect(style?.flickerTarget).toBe(target)
      expect(style?.flickerPeriodS).toBe(period)
      expect(style?.flickerAlpha).toEqual(flicker)
      expect(style?.stripeBlinkAlpha).toEqual(blink)
      expect(style?.outlineAlpha).toEqual(outline)
    },
  )
})

describe('importance geometry', () => {
  it('places the skill icon 286 of 899.4 units in from the right and 22 below the box top', () => {
    expect(importanceGeometry('skill', false)).toEqual({
      iconSize: '22.237cqw',
      iconTop: '2.4461cqw',
      iconRight: '31.799cqw',
      bandHeight: '4cqw',
      bandOffset: '-1.6678cqw',
    })
  })

  it('moves a locked skill icon to 86 units from the right', () => {
    expect(importanceGeometry('skill', true).iconRight).toBe('9.5619cqw')
  })

  it('pins the passive icon to the right edge of the 1304.2-unit box, 3 units above it', () => {
    expect(importanceGeometry('passive', false)).toEqual({
      iconSize: '15.3351cqw',
      iconTop: '-0.23cqw',
      iconRight: '0cqw',
      bandHeight: '4cqw',
      bandOffset: '-3.067cqw',
    })
  })

  it('straddles the box edge with a band 4 % of the box width tall', () => {
    for (const variant of ['skill', 'passive'] as const) {
      const { bandHeight, bandOffset } = importanceGeometry(variant, false)
      const height = Number.parseFloat(bandHeight)
      const offset = Number.parseFloat(bandOffset)
      expect(height).toBe(4)
      expect(offset).toBeLessThan(0)
      expect(height + offset).toBeGreaterThan(0)
    }
  })
})

describe('passive banner', () => {
  it('sizes the name banner and its outline from the passive box', () => {
    const style = importanceBannerStyle(3, 'player') as Record<string, unknown>
    expect(style['--importance-banner-left']).toBe('2.1469cqw')
    expect(style['--importance-banner-width']).toBe('51.4492cqw')
    expect(style['--importance-banner-height']).toBe('5.5743cqw')
    expect(style['--importance-outline-side']).toBe('0.7668cqw')
    expect(style['--importance-outline-cap']).toBe('1.2383cqw')
    expect(style['--importance-outline-min']).toBe(0.5)
    expect(style['--importance-outline-max']).toBe(1)
    expect(style['--importance-flicker-period']).toBe('0.8s')
  })
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
