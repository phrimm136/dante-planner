import { renderHook, act } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { useTrackerDoneMarks } from '../useTrackerDoneMarks'

describe('useTrackerDoneMarks', () => {
  it('initializes with empty done marks', () => {
    const { result } = renderHook(() => useTrackerDoneMarks())

    expect(result.current.doneMarks).toEqual({})
    expect(result.current.egoGiftDoneMarks.size).toBe(0)
  })

  describe('Done Marks', () => {
    const packGifts = ['gift1', 'gift2', 'gift3']

    it('adds a done mark for a theme pack and syncs gifts', () => {
      const { result } = renderHook(() => useTrackerDoneMarks())

      act(() => {
        result.current.togglePackDone(0, 'themePack1', packGifts)
      })

      expect(result.current.doneMarks[0]?.has('themePack1')).toBe(true)
      packGifts.forEach((id) => expect(result.current.egoGiftDoneMarks.has(id)).toBe(true))
    })

    it('removes a done mark when toggled again and removes gifts', () => {
      const { result } = renderHook(() => useTrackerDoneMarks())

      act(() => {
        result.current.togglePackDone(0, 'themePack1', packGifts)
      })

      act(() => {
        result.current.togglePackDone(0, 'themePack1', packGifts)
      })

      expect(result.current.doneMarks[0]?.has('themePack1')).toBe(false)
      packGifts.forEach((id) => expect(result.current.egoGiftDoneMarks.has(id)).toBe(false))
    })

    it('handles multiple done marks on same floor', () => {
      const { result } = renderHook(() => useTrackerDoneMarks())

      act(() => {
        result.current.togglePackDone(0, 'themePack1', ['giftA'])
        result.current.togglePackDone(0, 'themePack2', ['giftB'])
      })

      expect(result.current.doneMarks[0]?.has('themePack1')).toBe(true)
      expect(result.current.doneMarks[0]?.has('themePack2')).toBe(true)
    })

    it('handles done marks on different floors independently', () => {
      const { result } = renderHook(() => useTrackerDoneMarks())

      act(() => {
        result.current.togglePackDone(0, 'themePack1', ['giftA'])
        result.current.togglePackDone(1, 'themePack2', ['giftB'])
      })

      expect(result.current.doneMarks[0]?.has('themePack1')).toBe(true)
      expect(result.current.doneMarks[1]?.has('themePack2')).toBe(true)
      expect(result.current.doneMarks[0]?.has('themePack2')).toBe(false)
    })

    it('does not affect pack mark when individual gift is toggled', () => {
      const { result } = renderHook(() => useTrackerDoneMarks())

      act(() => {
        result.current.togglePackDone(0, 'themePack1', packGifts)
      })

      act(() => {
        result.current.toggleEgoGiftDoneMark('gift1')
      })

      expect(result.current.doneMarks[0]?.has('themePack1')).toBe(true)
      expect(result.current.egoGiftDoneMarks.has('gift1')).toBe(false)
      expect(result.current.egoGiftDoneMarks.has('gift2')).toBe(true)
    })
  })

  it('preserves marks across re-renders', () => {
    const { result, rerender } = renderHook(() => useTrackerDoneMarks())

    act(() => {
      result.current.togglePackDone(2, 'themePack3', ['giftX'])
    })

    rerender()

    expect(result.current.doneMarks[2]?.has('themePack3')).toBe(true)
  })
})
