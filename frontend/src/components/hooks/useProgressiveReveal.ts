import { useState, useEffect } from 'react'
import { PROGRESSIVE_REVEAL } from '@/lib/constants'

export function useProgressiveReveal(
  sectionCount: number,
  staggerDelay: number = PROGRESSIVE_REVEAL.STAGGER_DELAY,
): boolean[] {
  const [revealedCount, setRevealedCount] = useState(1)

  useEffect(() => {
    if (revealedCount >= sectionCount) return

    const timer = setTimeout(() => {
      setRevealedCount((prev) => Math.min(prev + 1, sectionCount))
    }, staggerDelay)

    return () => clearTimeout(timer)
  }, [revealedCount, sectionCount, staggerDelay])

  return Array.from({ length: sectionCount }, (_, index) => index < revealedCount)
}

export interface ProgressiveCountOptions {
  total: number
  step: number
  initial: number
}

export function useProgressiveCount({ total, step, initial }: ProgressiveCountOptions): number {
  const [count, setCount] = useState(initial)

  // Keyed on the size, never on the items' identity: callers derive their array during render,
  useEffect(() => {
    setCount(initial)
  }, [total, initial])

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
