import { EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'

export const OBSERVATION_LIST_ROWS = 3

const OBSERVATION_LIST_GAP_PX = 8

const OBSERVATION_LIST_PADDING_PX = 16

export function observationListHeightPx(slotHeightPx: number): number {
  return (
    OBSERVATION_LIST_ROWS * slotHeightPx +
    (OBSERVATION_LIST_ROWS - 1) * OBSERVATION_LIST_GAP_PX +
    2 * OBSERVATION_LIST_PADDING_PX
  )
}

const CARD_WIDTH_PX = EGO_GIFT_GEOMETRY.size.widthPx

function pxToCardPercent(px: number): number {
  return (px / CARD_WIDTH_PX) * 100
}

export function pct(value: number): string {
  return `${String(value)}%`
}

export function cqw(value: number): string {
  return `${String(value)}cqw`
}

export const EGO_GIFT_CARD = {
  enhancedOverlay: pxToCardPercent(72),
  icon: { size: pxToCardPercent(72), liftY: pxToCardPercent(3) },
  tierIcon: { size: pxToCardPercent(28), top: pxToCardPercent(6), left: pxToCardPercent(4) },
  tierText: {
    fontSize: pxToCardPercent(34),
    top: 0,
    left: pxToCardPercent(8),
    liftY: pxToCardPercent(4),
  },
  enhancement: {
    1: { height: pxToCardPercent(22), top: pxToCardPercent(8), right: pxToCardPercent(8) },
    2: { height: pxToCardPercent(26), top: pxToCardPercent(6), right: pxToCardPercent(6) },
  },
  keywordIcon: pxToCardPercent(24),
} as const
