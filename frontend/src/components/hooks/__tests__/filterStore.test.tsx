import { renderHook, act } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { createFilterStore, useFilterStore } from '../filterStore'

function emptyHandle() {
  return createFilterStore({
    sinners: new Set<string>(),
    keywords: new Set<string>(),
    raritys: new Set<number>(),
  })
}

describe('createFilterStore', () => {
  it('exposes the initial Sets under values', () => {
    const handle = createFilterStore({
      sinners: new Set<string>(['yisang']),
      raritys: new Set<number>(),
    })

    expect(handle.store.getState().values.sinners).toEqual(new Set(['yisang']))
    expect(handle.store.getState().values.raritys).toEqual(new Set())
    expect(handle.store.getState().searchQuery).toBe('')
  })

  it('sets a single filter without touching the others', () => {
    const handle = emptyHandle()

    handle.setters.sinners(new Set(['faust']))

    expect(handle.store.getState().values.sinners).toEqual(new Set(['faust']))
    expect(handle.store.getState().values.keywords).toEqual(new Set())
  })

  it('keeps the filters after every reader has unmounted', () => {
    const handle = emptyHandle()

    const first = renderHook(() => useFilterStore(handle))
    act(() => {
      first.result.current.setters.keywords(new Set(['Burn']))
      first.result.current.setSearchQuery('rupture')
    })
    first.unmount()

    expect(handle.store.getState().values.keywords).toEqual(new Set(['Burn']))

    const second = renderHook(() => useFilterStore(handle))

    expect(second.result.current.values.keywords).toEqual(new Set(['Burn']))
    expect(second.result.current.searchQuery).toBe('rupture')
  })

  it('resetAll restores the initial record and the empty query', () => {
    const handle = createFilterStore({
      sinners: new Set<string>(),
      keywords: new Set<string>(),
      raritys: new Set<number>(),
    })

    handle.setters.sinners(new Set(['heathcliff']))
    handle.setters.keywords(new Set(['Rupture', 'Sinking']))
    handle.setters.raritys(new Set([2, 3]))
    handle.setSearchQuery('sinking')

    handle.resetAll()

    expect(handle.store.getState().values).toEqual(handle.store.getInitialState().values)
    expect(handle.store.getState().searchQuery).toBe('')
  })

  it('resetAll covers keys that were never individually set', () => {
    const handle = emptyHandle()

    handle.setters.sinners(new Set(['don']))
    handle.resetAll()

    expect(handle.store.getState().values.sinners).toEqual(new Set())
    expect(handle.store.getState().values.keywords).toEqual(new Set())
    expect(handle.store.getState().values.raritys).toEqual(new Set())
  })
})

describe('useFilterStore', () => {
  it('re-renders the reader when a filter changes', () => {
    const handle = emptyHandle()
    const { result } = renderHook(() => useFilterStore(handle))

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

  it('re-renders the reader when the query changes', () => {
    const handle = emptyHandle()
    const { result } = renderHook(() => useFilterStore(handle))

    act(() => {
      result.current.setSearchQuery('faust')
    })

    expect(result.current.searchQuery).toBe('faust')
  })

  it('hands the reader the store every slot subscribes through', () => {
    const handle = emptyHandle()
    const { result } = renderHook(() => useFilterStore(handle))

    expect(result.current.store).toBe(handle.store)
  })
})
