import type { CardGeometry } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE } from '@/lib/constants'

export const BATTLE_GEOMETRY: CardGeometry = {
  size: { widthPx: 224, heightPx: 256 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}
