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
  staggerDelay: number = PROGRESSIVE_REVEAL.STAGGER_DELAY
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
