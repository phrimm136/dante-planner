import { useState, useEffect } from 'react'
import { PROGRESSIVE_REVEAL } from '@/lib/constants'

/**
 * Progressive reveal hook for staggered section rendering
 *
 * Reveals sections one at a time with a configurable stagger delay.
 * Used to improve perceived performance by showing content sequentially
 * rather than all at once with loading skeletons.
 *
 * @param sectionCount - Total number of sections to reveal
 * @param staggerDelay - Delay between each section reveal (ms), defaults to PROGRESSIVE_REVEAL.STAGGER_DELAY
 * @returns Array of booleans indicating which sections are visible
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const visibleSections = useProgressiveReveal(5)
 *
 *   return (
 *     <div>
 *       {visibleSections[0] && <Section1 />}
 *       {visibleSections[1] && <Section2 />}
 *       {visibleSections[2] && <Section3 />}
 *     </div>
 *   )
 * }
 * ```
 */
export function useProgressiveReveal(
  sectionCount: number,
  staggerDelay: number = PROGRESSIVE_REVEAL.STAGGER_DELAY,
): boolean[] {
  const [revealedCount, setRevealedCount] = useState(1) // Start with first section visible

  useEffect(() => {
    if (revealedCount >= sectionCount) return

    const timer = setTimeout(() => {
      setRevealedCount((prev) => Math.min(prev + 1, sectionCount))
    }, staggerDelay)

    return () => clearTimeout(timer)
  }, [revealedCount, sectionCount, staggerDelay])

  // Generate array of booleans for each section's visibility
  return Array.from({ length: sectionCount }, (_, index) => index < revealedCount)
}

export interface ProgressiveCountOptions {
  /** Upper bound the count grows toward (e.g. items.length or a section total) */
  total: number
  /** Increment applied per animation frame */
  step: number
  /** Starting count on mount and after a reset */
  initial: number
  /** When this reference changes, the count resets to `initial` (e.g. the sorted items array) */
  resetKey?: unknown
}

/**
 * Progressive rendering counter driven by requestAnimationFrame.
 *
 * Grows a count from `initial` toward `total` by `step` per animation frame.
 * Used by list grids (render the first batch, then a batch per frame) and by
 * detail pages (reveal sections one per frame). Complements useProgressiveReveal,
 * which staggers by a setTimeout delay instead of animation frames.
 *
 * @example
 * ```tsx
 * const displayCount = useProgressiveCount({
 *   total: sortedItems.length,
 *   step: PROGRESSIVE_REVEAL.CARD_BATCH,
 *   initial: PROGRESSIVE_REVEAL.CARD_BATCH,
 *   resetKey: sortedItems,
 * })
 * return sortedItems.slice(0, displayCount).map(...)
 * ```
 */
export function useProgressiveCount({
  total,
  step,
  initial,
  resetKey,
}: ProgressiveCountOptions): number {
  const [count, setCount] = useState(initial)

  // Reset when the underlying data changes (new data loaded)
  useEffect(() => {
    setCount(initial)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset is keyed on data identity only
  }, [resetKey])

  useEffect(() => {
    if (count < total) {
      const rafId = requestAnimationFrame(() => {
        setCount((prev) => Math.min(prev + step, total))
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [count, total, step])

  return count
}
