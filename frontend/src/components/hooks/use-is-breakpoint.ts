'use client'

import { useState, useSyncExternalStore } from 'react'

type BreakpointMode = 'min' | 'max'

function buildQuery(mode: BreakpointMode, breakpoint: number): string {
  return mode === 'min' ? `(min-width: ${breakpoint}px)` : `(max-width: ${breakpoint - 1}px)`
}

function getServerSnapshot(): boolean {
  return false
}

function watch(query: string): { query: string; list: MediaQueryList | null } {
  return { query, list: typeof window === 'undefined' ? null : window.matchMedia(query) }
}

export function useIsBreakpoint(mode: BreakpointMode = 'max', breakpoint = 768) {
  const query = buildQuery(mode, breakpoint)
  const [watched, setWatched] = useState(() => watch(query))
  if (watched.query !== query) {
    setWatched(watch(query))
  }

  const { list } = watched
  const subscribe = (onStoreChange: () => void) => {
    if (!list) return () => {}
    list.addEventListener('change', onStoreChange)
    return () => list.removeEventListener('change', onStoreChange)
  }

  return useSyncExternalStore(subscribe, () => list?.matches ?? false, getServerSnapshot)
}
