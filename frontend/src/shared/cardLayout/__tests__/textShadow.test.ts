import { describe, it, expect } from 'vitest'

import { levelShadow, nameShadow, underlayShadow } from '../textShadow'

describe('nameShadow', () => {
  it('draws the Korean underlay unblurred, the game softness being zero', () => {
    expect(nameShadow('identity', 'KR')?.split(', ')[0]).toBe('0.53295cqw 0.53295cqw 0cqw #040001')
  })

  it('spreads a dilated underlay into a ring at the dilate distance', () => {
    const layers = nameShadow('identity', 'KR')?.split(', ') ?? []

    expect(layers).toHaveLength(9)
    expect(layers).toContain('0.71948cqw 0.53295cqw 0cqw #040001')
    expect(layers).toContain('0.34642cqw 0.53295cqw 0cqw #040001')
    expect(layers).toContain('0.53295cqw 0.34642cqw 0cqw #040001')
    expect(layers).toContain('0.53295cqw 0.71948cqw 0cqw #040001')
  })

  it('leaves an undilated underlay as the single offset layer', () => {
    expect(nameShadow('ego', 'JP')).toBe('0.34821cqw 0.30725cqw 0cqw #040001')
  })

  it('draws the Japanese underlay with its own asymmetric offset', () => {
    expect(nameShadow('identity', 'JP')).toBe('0.45867cqw 0.40471cqw 0cqw #040001')
  })

  it('draws the English identity name on the Mikodacs underline material', () => {
    expect(nameShadow('identity', 'EN')).toBe('0.51747cqw 0.51747cqw 0cqw #040001')
  })

  it('decorates the English EGO and theme pack names', () => {
    expect(nameShadow('ego', 'EN')).toBe('0.39286cqw 0.39286cqw 0cqw #040001')
    expect(nameShadow('themePack', 'EN')).toBe('0.42876cqw 0.42876cqw 0cqw #040001')
  })

  it('gives Chinese the Korean row on every card', () => {
    expect(nameShadow('identity', 'CN')).toBe(nameShadow('identity', 'KR'))
    expect(nameShadow('ego', 'CN')).toBe(nameShadow('ego', 'KR'))
    expect(nameShadow('themePack', 'CN')).toBe(nameShadow('themePack', 'KR'))
  })

  it('leaves a language the game ships no face for undecorated', () => {
    expect(nameShadow('ego', 'FR')).toBeUndefined()
  })

  it.each([
    ['identity', 'KR'],
    ['identity', 'JP'],
    ['ego', 'KR'],
    ['ego', 'EN'],
    ['ego', 'JP'],
    ['themePack', 'KR'],
    ['themePack', 'EN'],
    ['themePack', 'JP'],
  ] as const)('drops the %s underlay below the %s name, never above it', (card, language) => {
    const offsets = (nameShadow(card, language) ?? '')
      .split(', ')
      .map((layerText) => layerText.split(' '))

    expect(offsets.length).toBeGreaterThan(0)
    expect(Number.parseFloat(offsets[0]?.[0] ?? '0')).toBeGreaterThan(0)
    expect(Number.parseFloat(offsets[0]?.[1] ?? '0')).toBeGreaterThan(0)
  })
})

describe('levelShadow', () => {
  it('draws the identity level underlay as a single undilated offset', () => {
    expect(levelShadow('identity')).toBe('0.50679cqw 0.50679cqw 0cqw #040001')
  })

  it('drops the identity level underlay below the text, never above it', () => {
    const [dx, dy] = (levelShadow('identity') ?? '').split(' ')

    expect(Number.parseFloat(dx ?? '0')).toBeGreaterThan(0)
    expect(Number.parseFloat(dy ?? '0')).toBeGreaterThan(0)
  })

  it('leaves the cards whose level the game does not decorate undecorated', () => {
    expect(levelShadow('ego')).toBeUndefined()
    expect(levelShadow('themePack')).toBeUndefined()
  })
})

describe('underlayShadow', () => {
  const ring = (shadow: string) =>
    shadow
      .split(', ')
      .slice(1)
      .map((layerText) => layerText.split(' ').map((part) => Number(part.replace('cqw', ''))))

  it('spends an unsoftened dilate entirely on the ring', () => {
    const shadow = underlayShadow({ dx: 0, dy: 0, softness: 0, dilate: 0.4 }, '#000')

    expect(shadow.split(', ')).toHaveLength(9)
    for (const [dx, dy, blur] of ring(shadow)) {
      expect(Math.hypot(dx ?? 0, dy ?? 0)).toBeCloseTo(0.4, 5)
      expect(blur).toBe(0)
    }
  })

  it('splits a partly softened dilate, so the reach stays the dilate', () => {
    const shadow = underlayShadow({ dx: 0, dy: 0, softness: 0.3, dilate: 0.4 }, '#000')

    expect(shadow.split(', ')).toHaveLength(9)
    for (const [dx, dy, blur] of ring(shadow)) {
      expect(Math.hypot(dx ?? 0, dy ?? 0) + (blur ?? 0)).toBeCloseTo(0.4, 5)
      expect(blur).toBe(0.3)
    }
  })

  it('drops the ring where the softness already reaches the dilate', () => {
    expect(underlayShadow({ dx: 0, dy: 0, softness: 0.4, dilate: 0.4 }, '#000')).toBe(
      '0cqw 0cqw 0.4cqw #000',
    )
  })

  it('never pulls the ring inside the glyph when the softness overruns the dilate', () => {
    expect(underlayShadow({ dx: 0, dy: 0, softness: 0.6, dilate: 0.4 }, '#000')).toBe(
      '0cqw 0cqw 0.6cqw #000',
    )
  })

  it('carries the underlay`s own offset into every layer', () => {
    const shadow = underlayShadow({ dx: 0.5, dy: 0.5, softness: 0, dilate: 0.4 }, '#000')

    expect(shadow.split(', ')[0]).toBe('0.5cqw 0.5cqw 0cqw #000')
    for (const [dx, dy] of ring(shadow)) {
      expect(Math.hypot((dx ?? 0) - 0.5, (dy ?? 0) - 0.5)).toBeCloseTo(0.4, 5)
    }
  })
})

describe('card name and level shadows, layer for layer', () => {
  it.each([
    [
      'identity KR',
      nameShadow('identity', 'KR'),
      '0.53295cqw 0.53295cqw 0cqw #040001, 0.71948cqw 0.53295cqw 0cqw #040001, 0.34642cqw 0.53295cqw 0cqw #040001, 0.53295cqw 0.71948cqw 0cqw #040001, 0.53295cqw 0.34642cqw 0cqw #040001, 0.66485cqw 0.66485cqw 0cqw #040001, 0.66485cqw 0.40105cqw 0cqw #040001, 0.40105cqw 0.66485cqw 0cqw #040001, 0.40105cqw 0.40105cqw 0cqw #040001',
    ],
    ['identity EN', nameShadow('identity', 'EN'), '0.51747cqw 0.51747cqw 0cqw #040001'],
    ['identity JP', nameShadow('identity', 'JP'), '0.45867cqw 0.40471cqw 0cqw #040001'],
    ['identity level', levelShadow('identity'), '0.50679cqw 0.50679cqw 0cqw #040001'],
    [
      'ego KR',
      nameShadow('ego', 'KR'),
      '0.40461cqw 0.40461cqw 0cqw #040001, 0.54622cqw 0.40461cqw 0cqw #040001, 0.263cqw 0.40461cqw 0cqw #040001, 0.40461cqw 0.54622cqw 0cqw #040001, 0.40461cqw 0.263cqw 0cqw #040001, 0.50474cqw 0.50474cqw 0cqw #040001, 0.50474cqw 0.30448cqw 0cqw #040001, 0.30448cqw 0.50474cqw 0cqw #040001, 0.30448cqw 0.30448cqw 0cqw #040001',
    ],
    ['ego EN', nameShadow('ego', 'EN'), '0.39286cqw 0.39286cqw 0cqw #040001'],
    ['ego JP', nameShadow('ego', 'JP'), '0.34821cqw 0.30725cqw 0cqw #040001'],
    [
      'themePack KR',
      nameShadow('themePack', 'KR'),
      '0.44159cqw 0.44159cqw 0cqw #040001, 0.59615cqw 0.44159cqw 0cqw #040001, 0.28703cqw 0.44159cqw 0cqw #040001, 0.44159cqw 0.59615cqw 0cqw #040001, 0.44159cqw 0.28703cqw 0cqw #040001, 0.55088cqw 0.55088cqw 0cqw #040001, 0.55088cqw 0.3323cqw 0cqw #040001, 0.3323cqw 0.55088cqw 0cqw #040001, 0.3323cqw 0.3323cqw 0cqw #040001',
    ],
    ['themePack EN', nameShadow('themePack', 'EN'), '0.42876cqw 0.42876cqw 0cqw #040001'],
    ['themePack JP', nameShadow('themePack', 'JP'), '0.38004cqw 0.33533cqw 0cqw #040001'],
  ])('draws %s as the game`s own material does', (_label, shadow, expected) => {
    expect(shadow).toBe(expected)
  })
})
