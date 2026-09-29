import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import { COIN_CLIENT_WIDTH, IMPORTANCE_DECORATION } from '@/lib/constants'
import {
  clientLength,
  hasImportance,
  importanceBannerStyle,
  importanceBoxPadding,
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
  it('sizes client units against the text line: 50 units to one 1.25rem line', () => {
    expect(clientLength(50)).toBe('1.25rem')
    expect(clientLength(COIN_CLIENT_WIDTH)).toBe('1.25rem')
  })

  it('places the in-game 159-unit skill triangle 95 units in from the right and 45 down', () => {
    expect(importanceGeometry('skill', false)).toEqual({
      iconSize: '3.975rem',
      iconTop: '1.125rem',
      iconRight: '2.375rem',
      bandHeight: '0.675rem',
      bandOffset: '0rem',
    })
  })

  it('moves a locked skill icon to 86 units from the right', () => {
    expect(importanceGeometry('skill', true).iconRight).toBe('2.15rem')
  })

  it('draws the passive triangle at the in-game 163 units, at the right edge, 14 units down', () => {
    expect(importanceGeometry('passive', false)).toEqual({
      iconSize: '4.075rem',
      iconTop: '0.35rem',
      iconRight: '0rem',
      bandHeight: '0.675rem',
      bandOffset: '0rem',
    })
  })

  it('keeps the skill bands inside the box so stacked boxes never overlap', () => {
    const { bandHeight, bandOffset } = importanceGeometry('skill', false)
    expect(Number.parseFloat(bandOffset)).toBe(0)
    expect(Number.parseFloat(bandHeight)).toBeLessThan(1)
  })
})

describe('passive banner', () => {
  it('tints the name plate itself and glows around it', () => {
    const style = importanceBannerStyle(3, 'player') as Record<string, unknown>
    expect(style['--name-body']).toMatch(/^#87cb00(ff)?$/i)
    expect(style['--name-text']).toMatch(/^#f6ff00(ff)?$/i)
    expect(style['--importance-glow-radius']).toBe('0.15rem')
    expect(style['--importance-glow-min']).toMatch(/ 50%, transparent\)$/)
    expect(style['--importance-glow-max']).toMatch(/ 100%, transparent\)$/)
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

describe('importance box padding', () => {
  it('pads a decorated passive box by one band and bleeds it to the pane edges', () => {
    expect(importanceBoxPadding(3)).toEqual({
      paddingBlock: '0.675rem',
      paddingInline: '1rem',
      marginInline: '-1rem',
    })
    expect(importanceBoxPadding(0)).toBeUndefined()
  })
})
