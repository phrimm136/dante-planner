import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'

import { useIsBreakpoint } from '../use-is-breakpoint'

interface FakeMediaQueryList {
  matches: boolean
  media: string
  addEventListener: (type: string, listener: (event: MediaQueryListEvent) => void) => void
  removeEventListener: (type: string, listener: (event: MediaQueryListEvent) => void) => void
  emit: (matches: boolean) => void
}

const originalMatchMedia = window.matchMedia

function latestList(lists: FakeMediaQueryList[]): FakeMediaQueryList {
  const list = lists.at(-1)
  if (!list) throw new Error('window.matchMedia was never called')
  return list
}

function stubMatchMedia(initialWidth: number) {
  const lists: FakeMediaQueryList[] = []

  window.matchMedia = vi.fn((query: string) => {
    const min = query.match(/\(min-width:\s*(\d+)px\)/)
    const max = query.match(/\(max-width:\s*(\d+)px\)/)
    const listeners = new Set<(event: MediaQueryListEvent) => void>()
    const list: FakeMediaQueryList = {
      matches: min ? initialWidth >= Number(min[1]) : max ? initialWidth <= Number(max[1]) : false,
      media: query,
      addEventListener: (_type, listener) => listeners.add(listener),
      removeEventListener: (_type, listener) => listeners.delete(listener),
      emit: (matches) => {
        list.matches = matches
        listeners.forEach((listener) => listener({ matches } as MediaQueryListEvent))
      },
    }
    lists.push(list)
    return list as unknown as MediaQueryList
  }) as unknown as typeof window.matchMedia

  return lists
}

afterEach(() => {
  window.matchMedia = originalMatchMedia
  vi.restoreAllMocks()
})

describe('useIsBreakpoint', () => {
  it('computes the initial value synchronously from the media query', () => {
    stubMatchMedia(1280)

    const { result } = renderHook(() => useIsBreakpoint('min', 1024))

    expect(result.current).toBe(true)
  })

  it('builds a min-width query for "min" and an exclusive max-width query for "max"', () => {
    stubMatchMedia(1280)

    renderHook(() => useIsBreakpoint('min', 1024))
    renderHook(() => useIsBreakpoint('max', 768))

    const queries = vi.mocked(window.matchMedia).mock.calls.map(([query]) => query)
    expect(queries).toContain('(min-width: 1024px)')
    expect(queries).toContain('(max-width: 767px)')
  })

  it('updates only when the query crosses, not on every resize', () => {
    const lists = stubMatchMedia(800)

    const { result } = renderHook(() => useIsBreakpoint('min', 1024))
    expect(result.current).toBe(false)

    act(() => {
      latestList(lists).emit(true)
    })
    expect(result.current).toBe(true)
  })

  it('removes its listener on unmount', () => {
    const lists = stubMatchMedia(800)

    const { unmount } = renderHook(() => useIsBreakpoint('min', 1024))
    const list = latestList(lists)
    const removeSpy = vi.spyOn(list, 'removeEventListener')

    unmount()

    expect(removeSpy).toHaveBeenCalledWith('change', expect.any(Function))
  })
})
