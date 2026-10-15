import type { CardGeometry } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE } from '@/lib/constants'

export const BATTLE_GEOMETRY: CardGeometry = {
  size: { widthPx: 224, heightPx: 256 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

const BATTLE_DETAIL_SPRITE_ASPECT = { width: 16, height: 9 } as const

export const BATTLE_DETAIL_SPRITE_ASPECT_RATIO = `${String(BATTLE_DETAIL_SPRITE_ASPECT.width)} / ${String(BATTLE_DETAIL_SPRITE_ASPECT.height)}`

export const BATTLE_DETAIL_SPRITE = {
  unitsAcross: 10,
  boxHeight: BATTLE_DETAIL_SPRITE_ASPECT.height / BATTLE_DETAIL_SPRITE_ASPECT.width,
  massFill: 0.8,
  drawnFill: 0.95,
  massMiddleY: 0.52,
} as const
