/**
 * A card's box, in integer device-independent pixels.
 *
 * A box lives in its card's `CardGeometry`; a ratio is derived from one, never stored.
 */
export interface CardSizePx {
  widthPx: number
  heightPx: number
}

export function aspectOf(size: CardSizePx): number {
  return size.widthPx / size.heightPx
}

export type GridRowHeight = 'slot' | 'content'

/**
 * A card's sizing, read by a grid and by every slot in it.
 *
 * Slots are memoized on it, so it must keep a stable identity across renders — declare it
 * once per list rather than inline at the call site.
 */
export interface CardGeometry {
  size: CardSizePx
  mobileScale: number
  rows: GridRowHeight
}
