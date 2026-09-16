import { EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'

/** The rows the observation selection column shows at and above `sm`. */
export const OBSERVATION_LIST_ROWS = 3

/** `gap-2` between the column's slots. */
const OBSERVATION_LIST_GAP_PX = 8

/** `p-4` around the column. */
const OBSERVATION_LIST_PADDING_PX = 16

/** The column's height: its rows, the gaps between them and its own padding. */
export function observationListHeightPx(slotHeightPx: number): number {
  return (
    OBSERVATION_LIST_ROWS * slotHeightPx +
    (OBSERVATION_LIST_ROWS - 1) * OBSERVATION_LIST_GAP_PX +
    2 * OBSERVATION_LIST_PADDING_PX
  )
}

/** The EGO gift card root's width, the base every number below is a share of. */
const CARD_WIDTH_PX = EGO_GIFT_GEOMETRY.size.widthPx

/** A pixel length on the 96px card, as a share of the card root. */
function pxToCardPercent(px: number): number {
  return (px / CARD_WIDTH_PX) * 100
}

/** A share of the card root, as a CSS percentage. */
export function pct(value: number): string {
  return `${String(value)}%`
}

/** A share of the card root, as a CSS length in container-query width units. */
export function cqw(value: number): string {
  return `${String(value)}cqw`
}

/**
 * The EGO gift card's parts, as shares of the card root.
 *
 * Transcribed from the 96px card the transform wrapper used to scale: each number is the
 * pixel length it replaced, over 96.
 */
export const EGO_GIFT_CARD = {
  /** `bgEnhanced.webp`, drawn inside the base background */
  enhancedOverlay: pxToCardPercent(72),
  /** The gift icon, centred and lifted off the background's midline */
  icon: { size: pxToCardPercent(72), liftY: pxToCardPercent(3) },
  /** The EX tier sprite, upper-left */
  tierIcon: { size: pxToCardPercent(28), top: pxToCardPercent(6), left: pxToCardPercent(4) },
  /** The numeric tier, upper-left */
  tierText: {
    fontSize: pxToCardPercent(34),
    top: 0,
    left: pxToCardPercent(8),
    liftY: pxToCardPercent(4),
  },
  /** The `+1` / `+2` sprite, upper-right, keyed by enhancement level */
  enhancement: {
    1: { height: pxToCardPercent(22), top: pxToCardPercent(8), right: pxToCardPercent(8) },
    2: { height: pxToCardPercent(26), top: pxToCardPercent(6), right: pxToCardPercent(6) },
  },
  /** The keyword sprite, lower-right */
  keywordIcon: pxToCardPercent(24),
} as const
