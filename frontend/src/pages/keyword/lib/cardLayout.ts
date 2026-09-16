import type { CardGeometry } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE } from '@/lib/constants'

/** `KeywordCard`'s icon, which is square. */
export const KEYWORD_GEOMETRY: CardGeometry = {
  size: { widthPx: 96, heightPx: 96 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'content',
}
