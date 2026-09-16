import type { CSSProperties } from 'react'

/**
 * One node's box on a card, as a percentage of the card root.
 *
 * Transcribed from the game's RectTransforms, so a card scales by resizing its root
 * and nothing inside it carries a pixel size.
 */
export interface PctRect {
  left: number
  top: number
  width: number
  height: number
}

/** Absolute positioning for one `PctRect`, in percentages of the positioned ancestor. */
export function pctStyle(rect: PctRect): CSSProperties {
  return {
    position: 'absolute',
    left: `${String(rect.left)}%`,
    top: `${String(rect.top)}%`,
    width: `${String(rect.width)}%`,
    height: `${String(rect.height)}%`,
    maxWidth: 'none',
  }
}
