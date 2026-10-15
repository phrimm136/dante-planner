import { CARD_MOBILE_SCALE, CARD_MOBILE_SCALE_NONE } from '@/lib/constants'
import { IDENTITY_GEOMETRY, type CardGeometry } from '@/shared/cardLayout'

export const KEYWORD_ICON_GEOMETRY: CardGeometry = {
  size: { widthPx: 64, heightPx: 64 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

export const COMPACT_IDENTITY_GEOMETRY: CardGeometry = {
  size: { widthPx: 96, heightPx: 128 },
  mobileScale: CARD_MOBILE_SCALE_NONE,
  rows: 'slot',
}

export const SINNER_SKILL_GEOMETRY: CardGeometry = {
  size: { widthPx: 112, heightPx: 144 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

export const SKILL_IMAGE_GEOMETRY: CardGeometry = {
  size: { widthPx: 128, heightPx: 128 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

export const SKILL_EXCHANGE_GEOMETRY: CardGeometry = {
  size: { widthPx: 356, heightPx: 148 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

export const GIFT_ROW_PADDING_PX = 8

export function giftRowMinHeightPx(slotHeightPx: number): number {
  return slotHeightPx + 2 * GIFT_ROW_PADDING_PX
}

export const GIFT_GRID_ROWS = { md: 2, lg: 4 } as const

export function giftGridHeightPx(rows: number, slotHeightPx: number): number {
  return rows * slotHeightPx + (rows - 1) * GIFT_ROW_PADDING_PX + 2 * GIFT_ROW_PADDING_PX
}

function pxToCardPercent(px: number, base: number): number {
  return (px / base) * 100
}

export function pct(value: number): string {
  return `${String(value)}%`
}

export function cqw(value: number): string {
  return `${String(value)}cqw`
}

const keywordIcon = (px: number) => pxToCardPercent(px, KEYWORD_ICON_GEOMETRY.size.widthPx)
const sinnerSkill = (px: number) => pxToCardPercent(px, SINNER_SKILL_GEOMETRY.size.widthPx)
const skillImage = (px: number) => pxToCardPercent(px, SKILL_IMAGE_GEOMETRY.size.widthPx)
const skillExchange = (px: number) => pxToCardPercent(px, SKILL_EXCHANGE_GEOMETRY.size.widthPx)
const deck = (px: number) => pxToCardPercent(px, IDENTITY_GEOMETRY.size.widthPx)
const compactIdentity = (px: number) => pxToCardPercent(px, COMPACT_IDENTITY_GEOMETRY.size.widthPx)

export const KEYWORD_ICON_CARD = {
  icon: keywordIcon(48),
} as const

export const SINNER_SKILL_CARD = {
  padding: sinnerSkill(8),
  rowGap: sinnerSkill(4),
  portrait: sinnerSkill(96),
  skillGap: sinnerSkill(4),
  skillBox: sinnerSkill(28),
  atkIcon: sinnerSkill(20),
  badge: sinnerSkill(16),
  badgeOffset: sinnerSkill(4),
  badgeFontSize: sinnerSkill(10),
} as const

export const SKILL_IMAGE_CARD = {
  art: skillImage(64),
  atkComposite: skillImage(32),
  atkIcon: pxToCardPercent(16, 32),
  badge: skillImage(28),
  badgeOffset: skillImage(4),
  badgeFontSize: skillImage(14),
} as const

export const SKILL_EXCHANGE_CARD = {
  padding: skillExchange(8),
  gap: skillExchange(8),
  border: skillExchange(2),
  skill: skillExchange(128),
  arrowWidth: skillExchange(64),
  arrowHeight: skillExchange(32),
} as const

export const DECK_CARD = {
  padding: deck(8),
  rowGap: deck(4),
  skillGap: deck(4),
  skillBox: deck(28),
  atkIcon: deck(20),
  egoGap: deck(2),
  egoBox: deck(28),
  egoFallbackIcon: deck(16),
} as const

export const SINNER_DECK_GEOMETRY: CardGeometry = {
  size: { widthPx: 160, heightPx: 304 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'slot',
}

export const COMPACT_IDENTITY_CARD = {
  rowGap: compactIdentity(4),
  uptieIcon: compactIdentity(16),
  uptieInset: compactIdentity(2),
  levelFontSize: compactIdentity(16),
  levelBottom: compactIdentity(2),
  levelRight: compactIdentity(4),
  orderFontSize: compactIdentity(32),
  skillGap: compactIdentity(4),
  skillBox: compactIdentity(28),
  atkIcon: compactIdentity(20),
} as const

export const COMPACT_IDENTITY_GRID_GAP = 8

export const SKILL_REPLACEMENT_COLUMNS = { wide: 6, narrow: 3 } as const

export const SKILL_REPLACEMENT_GRID_GAP = 2

export const SINNER_GRID_GAP = 8

export const SINNER_GRID_COLUMNS = { lg: 6, md: 4, sm: 3, base: 2 } as const

export const START_BUFF_GRID_COLUMNS = 5

export const START_BUFF_SLOT_WIDTH_PX = 272
