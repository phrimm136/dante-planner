import { CARD_MOBILE_SCALE, CARD_MOBILE_SCALE_NONE } from '@/lib/constants'
import type { CardGeometry } from './geometry'

export const IDENTITY_GEOMETRY: CardGeometry = {
  size: { widthPx: 160, heightPx: 232 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

export const EGO_GEOMETRY: CardGeometry = {
  size: { widthPx: 160, heightPx: 206 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

export const EGO_GIFT_GEOMETRY: CardGeometry = {
  size: { widthPx: 96, heightPx: 96 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'content',
}

export const THEME_PACK_GEOMETRY: CardGeometry = {
  size: { widthPx: 240, heightPx: 395 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

export const PLANNER_GEOMETRY: CardGeometry = {
  size: { widthPx: 280, heightPx: 160 },
  mobileScale: CARD_MOBILE_SCALE_NONE,
  rows: 'content',
}
