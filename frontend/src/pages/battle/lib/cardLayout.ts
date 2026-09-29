import type { CardGeometry } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE } from '@/lib/constants'

export const BATTLE_GEOMETRY: CardGeometry = {
  size: { widthPx: 224, heightPx: 256 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

export const BATTLE_DETAIL_SPRITE_ASPECT_RATIO = '1 / 1'

export const BATTLE_CARD_SPRITE = {
  pxPerUnit: 53,
  anchorTopFraction: 0.15,
} as const
