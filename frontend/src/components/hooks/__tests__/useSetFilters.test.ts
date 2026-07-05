import { renderHook, act } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { useSetFilters } from '../useSetFilters'

describe('useSetFilters', () => {
  it('exposes the initial Sets under values', () => {
    const { result } = renderHook(() =>
      useSetFilters({
        sinners: new Set<string>(['yisang']),
        raritys: new Set<number>(),
      }),
    )

    expect(result.current.values.sinners).toEqual(new Set(['yisang']))
    expect(result.current.values.raritys).toEqual(new Set())
  })

  it('sets a single filter without touching the others', () => {
    const { result } = renderHook(() =>
      useSetFilters({
        sinners: new Set<string>(),
        keywords: new Set<string>(),
      }),
    )

    act(() => {
      result.current.setters.sinners(new Set(['faust']))
    })

    expect(result.current.values.sinners).toEqual(new Set(['faust']))
    expect(result.current.values.keywords).toEqual(new Set())
  })

  it('supports toggle-style updates through the setter', () => {
    const { result } = renderHook(() => useSetFilters({ keywords: new Set<string>() }))

    act(() => {
      result.current.setters.keywords(new Set(['Burn']))
    })
    expect(result.current.values.keywords).toEqual(new Set(['Burn']))

    act(() => {
      const next = new Set(result.current.values.keywords)
      next.delete('Burn')
      result.current.setters.keywords(next)
    })
    expect(result.current.values.keywords).toEqual(new Set())
  })

  it('resetAll clears every registered key', () => {
    const { result } = renderHook(() =>
      useSetFilters({
        sinners: new Set<string>(),
        keywords: new Set<string>(),
        raritys: new Set<number>(),
      }),
    )

    act(() => {
      result.current.setters.sinners(new Set(['heathcliff']))
      result.current.setters.keywords(new Set(['Rupture', 'Sinking']))
      result.current.setters.raritys(new Set([2, 3]))
    })

    act(() => {
      result.current.resetAll()
    })

    for (const key of Object.keys(result.current.values) as Array<
      keyof typeof result.current.values
    >) {
      expect(result.current.values[key].size).toBe(0)
    }
  })

  it('resetAll covers keys that were never individually set', () => {
    const { result } = renderHook(() =>
      useSetFilters({
        a: new Set<string>(['seed']),
        b: new Set<string>(),
      }),
    )

    act(() => {
      result.current.resetAll()
    })

    expect(result.current.values.a).toEqual(new Set())
    expect(result.current.values.b).toEqual(new Set())
  })
})
