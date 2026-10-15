import type { CSSProperties } from 'react'

import type { BattleSprite, SpritePlacement } from '../schemas/BattleSpecSchemas'
import { BATTLE_DETAIL_SPRITE } from './cardLayout'

const CQW_DECIMALS = 4

export interface HeaderSpriteFit {
  left: number
  top: number
  scale: number
}

function cqw(boxWidths: number): string {
  return `${String(Number((boxWidths * 100).toFixed(CQW_DECIMALS)))}cqw`
}

function span(low: number, high: number): number {
  return Math.max(high - low, 1)
}

function clampInside(offset: number, low: number, high: number, scale: number, size: number) {
  const start = offset + low * scale
  const end = offset + high * scale
  if (start < 0) return offset - start
  if (end > size) return offset - (end - size)
  return offset
}

export function worldScale(sprite: SpritePlacement): number {
  return 1 / (BATTLE_DETAIL_SPRITE.unitsAcross * sprite.ppu)
}

export function headerSpriteFit(sprite: SpritePlacement): HeaderSpriteFit {
  const { boxHeight, massFill, drawnFill, massMiddleY } = BATTLE_DETAIL_SPRITE
  const [massX0, massY0, massX1, massY1] = sprite.massBox
  const [drawnX0, drawnY0, drawnX1, drawnY1] = sprite.drawnBox
  const scale = Math.min(
    worldScale(sprite),
    massFill / span(massX0, massX1),
    (massFill * boxHeight) / span(massY0, massY1),
    drawnFill / span(drawnX0, drawnX1),
    (drawnFill * boxHeight) / span(drawnY0, drawnY1),
  )
  const left = 0.5 - sprite.centroid[0] * scale
  const top = massMiddleY * boxHeight - ((massY0 + massY1) / 2) * scale
  return {
    left: clampInside(left, drawnX0, drawnX1, scale, 1),
    top: clampInside(top, drawnY0, drawnY1, scale, boxHeight),
    scale,
  }
}

export function headerSpriteStyle(sprite: BattleSprite | undefined): CSSProperties | undefined {
  if (sprite === undefined || !('massBox' in sprite)) return undefined
  const { left, top, scale } = headerSpriteFit(sprite)
  return {
    position: 'absolute',
    maxWidth: 'none',
    width: cqw(sprite.width * scale),
    left: cqw(left),
    top: cqw(top),
  }
}
