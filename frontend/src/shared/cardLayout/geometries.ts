import { CARD_MOBILE_SCALE, CARD_MOBILE_SCALE_NONE } from '@/lib/constants'
import type { CardGeometry } from './geometry'

/**
 * The card boxes read by more than one slice.
 *
 * A geometry consumed across slices lives here rather than in the slice that draws the
 * card, so a reader never reaches through a slice's public API for a box.
 */

/** `IdentityCard`. */
export const IDENTITY_GEOMETRY: CardGeometry = {
  size: { widthPx: 160, heightPx: 232 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

/** `EGOCard`. */
export const EGO_GEOMETRY: CardGeometry = {
  size: { widthPx: 160, heightPx: 206 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

/** `EGOGiftCard`, which is square. */
export const EGO_GIFT_GEOMETRY: CardGeometry = {
  size: { widthPx: 96, heightPx: 96 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'content',
}

/** `ThemePackCard`. */
export const THEME_PACK_GEOMETRY: CardGeometry = {
  size: { widthPx: 240, heightPx: 395 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

/** `PlannerCard`. */
export const PLANNER_GEOMETRY: CardGeometry = {
  size: { widthPx: 280, heightPx: 160 },
  mobileScale: CARD_MOBILE_SCALE_NONE,
  rows: 'content',
}
