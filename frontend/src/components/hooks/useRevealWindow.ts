import { useEffect, useRef, useState } from 'react'
import type { RefObject } from 'react'

export interface RevealWindow {
  start: number
  down: number
  up: number
}

interface RevealWindowOptions {
  total: number
  step: number
  gridRef: RefObject<HTMLElement | null>
}

export function isIndexRevealed(revealWindow: RevealWindow, index: number): boolean {
  return (
    index >= revealWindow.start - revealWindow.up && index < revealWindow.start + revealWindow.down
  )
}

export function startIndexFor(
  scrollY: number,
  gridTop: number,
  rowHeight: number,
  columns: number,
): number {
  if (rowHeight <= 0 || columns <= 0) return 0
  const row = Math.floor(Math.max(0, scrollY - gridTop) / rowHeight)
  return row * columns
}

export function growWindow(revealWindow: RevealWindow, step: number, total: number): RevealWindow {
  return {
    start: revealWindow.start,
    down: Math.min(revealWindow.down + step, total - revealWindow.start),
    up: Math.min(revealWindow.up + step, revealWindow.start),
  }
}

export function isWindowComplete(revealWindow: RevealWindow, total: number): boolean {
  return (
    revealWindow.start - revealWindow.up <= 0 && revealWindow.start + revealWindow.down >= total
  )
}

// A browser resolves `grid-template-columns` to the used track list, one length per
export function columnCountFor(gridTemplateColumns: string): number {
  const tracks = gridTemplateColumns.split(/\s+/).filter((track) => /^[\d.]+px$/.test(track))
  return Math.max(tracks.length, 1)
}

function openWindowAt(grid: HTMLElement | null, step: number): RevealWindow {
  if (grid === null) return { start: 0, down: step, up: 0 }

  const columns = columnCountFor(getComputedStyle(grid).gridTemplateColumns)
  const firstCell = grid.firstElementChild
  const rowHeight = firstCell === null ? 0 : firstCell.getBoundingClientRect().height
  const gridTop = grid.getBoundingClientRect().top + window.scrollY

  return { start: startIndexFor(window.scrollY, gridTop, rowHeight, columns), down: step, up: 0 }
}

export function useRevealWindow({ total, step, gridRef }: RevealWindowOptions) {
  const [revealWindow, setRevealWindow] = useState<RevealWindow>({ start: 0, down: 0, up: 0 })
  const openedRef = useRef(false)

  useEffect(() => {
    if (openedRef.current && isWindowComplete(revealWindow, total)) return

    const frameId = requestAnimationFrame(() => {
      if (openedRef.current) {
        setRevealWindow((previous) => growWindow(previous, step, total))
        return
      }
      openedRef.current = true
      setRevealWindow(openWindowAt(gridRef.current, step))
    })

    return () => {
      cancelAnimationFrame(frameId)
    }
  }, [revealWindow, total, step, gridRef])

  return (index: number) => isIndexRevealed(revealWindow, index)
}
