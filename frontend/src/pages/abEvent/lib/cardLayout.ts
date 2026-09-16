import type { CardGeometry } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE } from '@/lib/constants'

/** `AbEventCard`: title over a 3:2 image. */
export const AB_EVENT_GEOMETRY: CardGeometry = {
  size: { widthPx: 308, heightPx: 245 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'content',
}
