'use client'

import { useEffect, useState } from 'react'

type BreakpointMode = 'min' | 'max'

function buildQuery(mode: BreakpointMode, breakpoint: number): string {
  return mode === 'min' ? `(min-width: ${breakpoint}px)` : `(max-width: ${breakpoint - 1}px)`
}

function getInitialMatches(mode: BreakpointMode, breakpoint: number): boolean {
  if (typeof window === 'undefined') return false
  return window.matchMedia(buildQuery(mode, breakpoint)).matches
}

export function useIsBreakpoint(mode: BreakpointMode = 'max', breakpoint = 768) {
  const [matches, setMatches] = useState<boolean>(() => getInitialMatches(mode, breakpoint))

  useEffect(() => {
    const mql = window.matchMedia(buildQuery(mode, breakpoint))
    const onChange = (e: MediaQueryListEvent) => setMatches(e.matches)

    setMatches(mql.matches)
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [mode, breakpoint])

  return matches
}
