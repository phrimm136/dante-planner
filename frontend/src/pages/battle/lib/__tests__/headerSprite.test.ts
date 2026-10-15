import { describe, it, expect } from 'vitest'

import type { SpritePlacement } from '../../schemas/BattleSpecSchemas'
import { BATTLE_DETAIL_SPRITE, BATTLE_DETAIL_SPRITE_ASPECT_RATIO } from '../cardLayout'
import { headerSpriteFit, headerSpriteStyle, worldScale } from '../headerSprite'

const SHOCK_CENTIPEDE: SpritePlacement = {
  file: '8045_ElectricCentipedeAppearance.webp',
  feetY: 1037.76,
  ppu: 240,
  anchorX: 965.17,
  anchorY: 24.96,
  width: 1930,
  height: 1041,
  massBox: [251, 280, 1733, 942],
  centroid: [843.24, 640.83],
  drawnBox: [49, 9, 1920, 1031],
}

const BRAZEN_BULL: SpritePlacement = {
  file: '8041_BrassBullAppearance.webp',
  feetY: 992.97,
  ppu: 240,
  anchorX: 973.74,
  anchorY: -112.23,
  width: 1946,
  height: 1120,
  massBox: [232, 137, 1611, 934],
  centroid: [909.57, 501.28],
  drawnBox: [10, 10, 1890, 1111],
}

const BLACK_THREAD: SpritePlacement = {
  file: '1503_BlackThreadAppearance.webp',
  feetY: 1028.54,
  ppu: 151.0622,
  anchorX: 1200.23,
  anchorY: -8.5,
  width: 2400,
  height: 1146,
  massBox: [359, 104, 1770, 994],
  centroid: [1117.27, 491.29],
  drawnBox: [192, 7, 2394, 1140],
}

const PWIE: SpritePlacement = {
  file: '400041_LittleBuffeAppearance.webp',
  feetY: 505.95,
  ppu: 240,
  anchorX: 148.9,
  anchorY: -17.25,
  width: 299,
  height: 522,
  massBox: [69, 75, 236, 455],
  centroid: [149.59, 267.79],
  drawnBox: [35, 8, 290, 514],
}

const BOX_HEIGHT = BATTLE_DETAIL_SPRITE.boxHeight

function drawnExtent(sprite: SpritePlacement) {
  const { left, top, scale } = headerSpriteFit(sprite)
  const [x0, y0, x1, y1] = sprite.drawnBox
  return {
    x0: left + x0 * scale,
    y0: top + y0 * scale,
    x1: left + x1 * scale,
    y1: top + y1 * scale,
  }
}

describe('detail header sprite', () => {
  it('frames the composite at 16:9', () => {
    expect(BATTLE_DETAIL_SPRITE_ASPECT_RATIO).toBe('16 / 9')
    expect(BOX_HEIGHT).toBe(9 / 16)
  })

  it.each([
    ['Shock Centipede 8045', SHOCK_CENTIPEDE],
    ['Brazen Bull 8041', BRAZEN_BULL],
    ['Black Thread 1503', BLACK_THREAD],
  ])('keeps the whole drawn box of %s inside the box', (_, sprite) => {
    const extent = drawnExtent(sprite)
    expect(extent.x0).toBeGreaterThanOrEqual(0)
    expect(extent.y0).toBeGreaterThanOrEqual(0)
    expect(extent.x1).toBeLessThanOrEqual(1)
    expect(extent.y1).toBeLessThanOrEqual(BOX_HEIGHT)
  })

  it.each([SHOCK_CENTIPEDE, BRAZEN_BULL, BLACK_THREAD, PWIE])(
    'never draws $file above world scale',
    (sprite) => {
      expect(headerSpriteFit(sprite).scale).toBeLessThanOrEqual(worldScale(sprite))
    },
  )

  it('draws a small unit at world scale with its centroid centred and mass middle at 52 %', () => {
    const { left, top, scale } = headerSpriteFit(PWIE)
    expect(scale).toBe(1 / 2400)
    expect(left + PWIE.centroid[0] * scale).toBeCloseTo(0.5, 10)
    expect(top + ((75 + 455) / 2) * scale).toBeCloseTo(0.52 * BOX_HEIGHT, 10)
  })

  it('shrinks a boss until its mass box fills at most 80 % of the box', () => {
    const { scale } = headerSpriteFit(BLACK_THREAD)
    expect(scale).toBeLessThan(worldScale(BLACK_THREAD))
    expect((1770 - 359) * scale).toBeLessThanOrEqual(0.8 + 1e-12)
    expect((994 - 104) * scale).toBeLessThanOrEqual(0.8 * BOX_HEIGHT + 1e-12)
  })

  it('sizes the image from the shipped width without the loaded image', () => {
    expect(headerSpriteStyle(PWIE)).toEqual({
      position: 'absolute',
      maxWidth: 'none',
      width: `${String(Number(((299 / 2400) * 100).toFixed(4)))}cqw`,
      left: `${String(Number(((0.5 - 149.59 / 2400) * 100).toFixed(4)))}cqw`,
      top: `${String(Number(((0.52 * BOX_HEIGHT - 265 / 2400) * 100).toFixed(4)))}cqw`,
    })
  })

  it('falls back when the sprite has no measures', () => {
    expect(headerSpriteStyle({ file: 'a.webp' })).toBeUndefined()
    expect(headerSpriteStyle(undefined)).toBeUndefined()
  })
})
