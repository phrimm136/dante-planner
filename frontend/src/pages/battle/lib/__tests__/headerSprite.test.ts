import { describe, it, expect } from 'vitest'

import { BATTLE_DETAIL_SPRITE_ASPECT_RATIO } from '../cardLayout'
import { headerSpriteStyle } from '../headerSprite'

const PLACED = { file: 'a.webp', feetY: 700, ppu: 250, anchorX: 500, anchorY: 100 }

describe('detail header sprite', () => {
  it('frames the composite at 16:9', () => {
    expect(BATTLE_DETAIL_SPRITE_ASPECT_RATIO).toBe('16 / 9')
  })

  it('draws ten world units across the box with the anchor 30 % from the top', () => {
    expect(headerSpriteStyle(PLACED, 1000)).toEqual({
      position: 'absolute',
      maxWidth: 'none',
      width: '40cqw',
      left: 'calc(50% - 20cqw)',
      top: 'calc(30% - 4cqw)',
    })
  })

  it('keeps a small unit small and a boss large at one scale', () => {
    const small = headerSpriteStyle({ ...PLACED, ppu: 240 }, 480)
    const boss = headerSpriteStyle({ ...PLACED, ppu: 60 }, 4000)
    expect(Number.parseFloat(String(small?.width))).toBeCloseTo(20, 4)
    expect(Number.parseFloat(String(boss?.width))).toBeCloseTo(666.6667, 3)
  })

  it('falls back when the sprite has no placement or has not loaded', () => {
    expect(headerSpriteStyle({ file: 'a.webp' }, 1000)).toBeUndefined()
    expect(headerSpriteStyle(PLACED, undefined)).toBeUndefined()
    expect(headerSpriteStyle(undefined, 1000)).toBeUndefined()
  })
})
