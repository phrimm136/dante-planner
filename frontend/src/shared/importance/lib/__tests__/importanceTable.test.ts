import { describe, expect, it } from 'vitest'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import { COIN_CLIENT_WIDTH } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import {
  hasImportance,
  importanceBannerStyle,
  importanceBoxPadding,
  importanceGeometry,
  importanceStyle,
  stripeTileWidth,
  toImportanceTier,
} from '../importanceTable'

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
  it('sizes client units against the description font: 40 client units are one 0.875rem description-font em', () => {
    expect(clientLength(40)).toBe('0.875rem')
    expect(clientLength(COIN_CLIENT_WIDTH)).toBe('1.4219rem')
    expect(clientLength(30)).toBe('0.6563rem')
    expect(clientLength(-10)).toBe('-0.2188rem')
  })

  it('sizes the skill triangle at 200 client units, 86 in from the right, 37 down', () => {
    expect(importanceGeometry('skill')).toEqual({
      iconSize: '4.375rem',
      iconTop: '0.8094rem',
      iconRight: '1.8813rem',
      bandHeight: '1.1412rem',
      bandOffset: '0rem',
    })
  })

  it.each([
    ['unlock', '1.8813rem'],
    ['base', '6.2562rem'],
  ] as const)(
    'places the skill triangle at its %s position, %s in from the right',
    (position, right) => {
      expect(importanceGeometry('skill', position).iconRight).toBe(right)
    },
  )

  it.each(['unlock', 'base'] as const)(
    'keeps the passive triangle at the right edge for the %s position',
    (position) => {
      expect(importanceGeometry('passive', position)).toEqual(importanceGeometry('passive'))
    },
  )

  it('sizes the passive triangle at 200 client units at the right edge, 37 down', () => {
    expect(importanceGeometry('passive')).toEqual({
      iconSize: '4.375rem',
      iconTop: '0.8094rem',
      iconRight: '0rem',
      bandHeight: '1.1412rem',
      bandOffset: '0rem',
    })
  })

  it('keeps every band inside its box at 52.2 client units', () => {
    for (const variant of ['skill', 'passive'] as const) {
      const { bandHeight, bandOffset } = importanceGeometry(variant)
      expect(bandHeight).toBe('1.1412rem')
      expect(bandOffset).toBe('0rem')
    }
  })
})

describe('passive banner', () => {
  it('tints the name plate itself and glows around it', () => {
    const style = importanceBannerStyle(3, 'player') as Record<string, unknown>
    expect(style['--name-body']).toMatch(/^#87cb00(ff)?$/i)
    expect(style['--name-text']).toMatch(/^#f6ff00(ff)?$/i)
    expect(style['--importance-glow-radius']).toBe('0.1313rem')
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
  it('spans one caution texture copy over 1304.2 client units', () => {
    expect(stripeTileWidth()).toBe('28.5294rem')
  })
})

describe('importance box padding', () => {
  it('pads a decorated passive box by the 40-unit importance group overhang and bleeds it to the pane edges', () => {
    expect(importanceBoxPadding(3)).toEqual({
      paddingBlock: '0.875rem',
      paddingInline: '1rem',
      marginInline: '-1rem',
    })
    expect(importanceBoxPadding(0)).toBeUndefined()
  })
})
