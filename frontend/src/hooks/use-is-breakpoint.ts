"use client"

import { useEffect, useState } from "react"

type BreakpointMode = "min" | "max"

function buildQuery(mode: BreakpointMode, breakpoint: number): string {
  return mode === "min"
    ? `(min-width: ${breakpoint}px)`
    : `(max-width: ${breakpoint - 1}px)`
}

function getInitialMatches(mode: BreakpointMode, breakpoint: number): boolean {
  if (typeof window === "undefined") return false
  return window.matchMedia(buildQuery(mode, breakpoint)).matches
}

/**
 * Hook to detect whether the current viewport matches a given breakpoint rule.
 * Initial value is computed synchronously to avoid a layout flash on mount.
 * Example:
 *   useIsBreakpoint("max", 768)   // true when width < 768
 *   useIsBreakpoint("min", 1024)  // true when width >= 1024
 */
export function useIsBreakpoint(
  mode: BreakpointMode = "max",
  breakpoint = 768
) {
  const [matches, setMatches] = useState<boolean>(() =>
    getInitialMatches(mode, breakpoint)
  )

  useEffect(() => {
    const mql = window.matchMedia(buildQuery(mode, breakpoint))
    const onChange = (e: MediaQueryListEvent) => setMatches(e.matches)

    setMatches(mql.matches)
    mql.addEventListener("change", onChange)
    return () => mql.removeEventListener("change", onChange)
  }, [mode, breakpoint])

  return matches
}
