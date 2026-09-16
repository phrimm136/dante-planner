import { describe, it, expect, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'

import { useViewportFillCount } from '../useViewportFillCount'

const originalWidth = window.innerWidth
const originalHeight = window.innerHeight

function setViewport(widthPx: number, heightPx: number) {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: widthPx })
  Object.defineProperty(window, 'innerHeight', { configurable: true, value: heightPx })
}

afterEach(() => {
  setViewport(originalWidth, originalHeight)
})

describe('useViewportFillCount', () => {
  it('counts the columns and rows that cover the viewport', () => {
    setViewport(1280, 800)

    const { result } = renderHook(() => useViewportFillCount({ widthPx: 96, heightPx: 96 }, 16))

    // ceil(1280 / 112) = 12 columns, ceil(800 / 112) = 8 rows
    expect(result.current).toBe(96)
  })

  it('rounds a partial column and a partial row up', () => {
    setViewport(200, 200)

    const { result } = renderHook(() => useViewportFillCount({ widthPx: 180, heightPx: 180 }, 0))

    expect(result.current).toBe(4)
  })

  it('holds the mount-time count across a resize', () => {
    setViewport(1280, 800)

    const { result, rerender } = renderHook(() =>
      useViewportFillCount({ widthPx: 96, heightPx: 96 }, 16),
    )

    setViewport(390, 400)
    rerender()

    expect(result.current).toBe(96)
  })
})
