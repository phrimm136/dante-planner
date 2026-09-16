import { useState } from 'react'

import type { CardSizePx } from '@/shared/cardLayout'

/** Tiles drawn where there is no window to measure. */
const FALLBACK_COUNT = 12

/** How many slots of `size`, laid out `gapPx` apart, cover the viewport. */
function fillCount(innerWidth: number, innerHeight: number, size: CardSizePx, gapPx: number) {
  const columns = Math.ceil(innerWidth / (size.widthPx + gapPx))
  const rows = Math.ceil(innerHeight / (size.heightPx + gapPx))

  return columns * rows
}

/**
 * The tile count that fills the viewport, read once at mount.
 *
 * A skeleton is transient, so the count is not resubscribed to resizes: the content it
 * stands in for replaces it before a resize can matter.
 */
export function useViewportFillCount(size: CardSizePx, gapPx: number): number {
  const [count] = useState(() =>
    typeof window === 'undefined'
      ? FALLBACK_COUNT
      : fillCount(window.innerWidth, window.innerHeight, size, gapPx),
  )

  return count
}
