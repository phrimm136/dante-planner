import { useState } from 'react'

import type { CardSizePx } from '@/shared/cardLayout'

const FALLBACK_COUNT = 12

function fillCount(innerWidth: number, innerHeight: number, size: CardSizePx, gapPx: number) {
  const columns = Math.ceil(innerWidth / (size.widthPx + gapPx))
  const rows = Math.ceil(innerHeight / (size.heightPx + gapPx))

  return columns * rows
}

export function useViewportFillCount(size: CardSizePx, gapPx: number): number {
  const [count] = useState(() =>
    typeof window === 'undefined'
      ? FALLBACK_COUNT
      : fillCount(window.innerWidth, window.innerHeight, size, gapPx),
  )

  return count
}
