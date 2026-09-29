import type { CSSProperties } from 'react'

import type { BattleSprite } from '../schemas/BattleSpecSchemas'
import { BATTLE_DETAIL_SPRITE } from './cardLayout'

const CQW_DECIMALS = 4

function cqw(value: number): string {
  return `${String(Number(value.toFixed(CQW_DECIMALS)))}cqw`
}

export function headerSpriteStyle(
  sprite: BattleSprite | undefined,
  naturalWidth: number | undefined,
): CSSProperties | undefined {
  if (sprite === undefined || !('ppu' in sprite) || naturalWidth === undefined) return undefined
  const cqwPerPixel = 100 / (BATTLE_DETAIL_SPRITE.unitsAcross * sprite.ppu)
  return {
    position: 'absolute',
    maxWidth: 'none',
    width: cqw(naturalWidth * cqwPerPixel),
    left: `calc(50% - ${cqw(sprite.anchorX * cqwPerPixel)})`,
    top: `calc(${String(BATTLE_DETAIL_SPRITE.anchorTopFraction * 100)}% - ${cqw(sprite.anchorY * cqwPerPixel)})`,
  }
}
