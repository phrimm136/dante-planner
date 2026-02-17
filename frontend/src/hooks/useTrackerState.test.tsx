import { renderHook, act } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { useTrackerState } from './useTrackerState'
import { DEFAULT_SKILL_EA, SINNERS } from '@/lib/constants'

// Default test props - hook requires initialEquipment and initialDeployment
const defaultInitialEquipment = {}
const defaultInitialDeployment: number[] = []

describe('useTrackerState', () => {
  describe('Initial State', () => {
    it('initializes with default skill EA values (3/2/1)', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      // Hook uses numeric string keys ("1", "2", etc.)
      expect(result.current.state.currentSkillCounts['1']).toEqual({
        0: DEFAULT_SKILL_EA[0],
        1: DEFAULT_SKILL_EA[1],
        2: DEFAULT_SKILL_EA[2],
      })
    })

    it('initializes with provided deployment order', () => {
      const initialDeployment = [0, 1, 2]
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, initialDeployment))

      expect(result.current.state.deploymentOrder).toEqual([0, 1, 2])
    })

    it('initializes with empty done marks', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      expect(result.current.state.doneMarks).toEqual({})
    })

    it('initializes skill counts for all 12 sinners', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      // Hook uses numeric string keys ("1" through "12")
      for (let i = 1; i <= SINNERS.length; i++) {
        expect(result.current.state.currentSkillCounts[String(i)]).toBeDefined()
      }
    })
  })

  describe('Deployment Order', () => {
    it('updates deployment order', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))
      const newOrder = [0, 1, 2]

      act(() => {
        result.current.setDeploymentOrder(newOrder)
      })

      expect(result.current.state.deploymentOrder).toEqual(newOrder)
    })

    it('preserves deployment order across multiple updates', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.setDeploymentOrder([0, 1])
      })

      act(() => {
        result.current.setDeploymentOrder([1, 0, 2])
      })

      expect(result.current.state.deploymentOrder).toEqual([1, 0, 2])
    })
  })

  describe('Current Skill Counts', () => {
    it('updates a single skill count', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.updateCurrentSkillCount('1', 0, 5)
      })

      expect(result.current.state.currentSkillCounts['1'][0]).toBe(5)
    })

    it('preserves other skill counts when updating one', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.updateCurrentSkillCount('1', 0, 5)
      })

      expect(result.current.state.currentSkillCounts['1'][1]).toBe(DEFAULT_SKILL_EA[1])
      expect(result.current.state.currentSkillCounts['1'][2]).toBe(DEFAULT_SKILL_EA[2])
    })

    it('updates skill counts for different sinners independently', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.updateCurrentSkillCount('1', 0, 5)
        result.current.updateCurrentSkillCount('2', 1, 4)
      })

      expect(result.current.state.currentSkillCounts['1'][0]).toBe(5)
      expect(result.current.state.currentSkillCounts['2'][1]).toBe(4)
    })

    it('allows skill count to be set to 0', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.updateCurrentSkillCount('1', 0, 0)
      })

      expect(result.current.state.currentSkillCounts['1'][0]).toBe(0)
    })
  })

  describe('Done Marks', () => {
    const packGifts = ['gift1', 'gift2', 'gift3']

    it('adds a done mark for a theme pack and syncs gifts', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.togglePackDone(0, 'themePack1', packGifts)
      })

      expect(result.current.state.doneMarks[0]?.has('themePack1')).toBe(true)
      packGifts.forEach((id) => expect(result.current.state.egoGiftDoneMarks.has(id)).toBe(true))
    })

    it('removes a done mark when toggled again and removes gifts', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.togglePackDone(0, 'themePack1', packGifts)
      })

      act(() => {
        result.current.togglePackDone(0, 'themePack1', packGifts)
      })

      expect(result.current.state.doneMarks[0]?.has('themePack1')).toBe(false)
      packGifts.forEach((id) => expect(result.current.state.egoGiftDoneMarks.has(id)).toBe(false))
    })

    it('handles multiple done marks on same floor', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.togglePackDone(0, 'themePack1', ['giftA'])
        result.current.togglePackDone(0, 'themePack2', ['giftB'])
      })

      expect(result.current.state.doneMarks[0]?.has('themePack1')).toBe(true)
      expect(result.current.state.doneMarks[0]?.has('themePack2')).toBe(true)
    })

    it('handles done marks on different floors independently', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.togglePackDone(0, 'themePack1', ['giftA'])
        result.current.togglePackDone(1, 'themePack2', ['giftB'])
      })

      expect(result.current.state.doneMarks[0]?.has('themePack1')).toBe(true)
      expect(result.current.state.doneMarks[1]?.has('themePack2')).toBe(true)
      expect(result.current.state.doneMarks[0]?.has('themePack2')).toBe(false)
    })

    it('does not affect pack mark when individual gift is toggled', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.togglePackDone(0, 'themePack1', packGifts)
      })

      // Uncheck one gift individually
      act(() => {
        result.current.toggleEgoGiftDoneMark('gift1')
      })

      // Pack remains marked done
      expect(result.current.state.doneMarks[0]?.has('themePack1')).toBe(true)
      // Gift is unchecked
      expect(result.current.state.egoGiftDoneMarks.has('gift1')).toBe(false)
      // Other gifts remain checked
      expect(result.current.state.egoGiftDoneMarks.has('gift2')).toBe(true)
    })
  })

  describe('Reset State', () => {
    it('resets all state to defaults', () => {
      const initialEquipment = { '1': { identityId: 'test', ego: {} } }
      const initialDeployment = [0, 1, 2]
      const { result } = renderHook(() => useTrackerState(initialEquipment, initialDeployment))

      // Make changes
      act(() => {
        result.current.setDeploymentOrder([3, 4, 5])
        result.current.updateCurrentSkillCount('1', 0, 5)
        result.current.togglePackDone(0, 'themePack1', ['gift1'])
      })

      // Reset with initial values
      act(() => {
        result.current.resetState(initialEquipment, initialDeployment)
      })

      // Verify reset
      expect(result.current.state.deploymentOrder).toEqual([0, 1, 2])
      expect(result.current.state.currentSkillCounts['1'][0]).toBe(DEFAULT_SKILL_EA[0])
      expect(result.current.state.doneMarks).toEqual({})
    })
  })

  describe('State Preservation', () => {
    it('preserves state across re-renders', () => {
      const { result, rerender } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.setDeploymentOrder([0, 1])
      })

      rerender()

      expect(result.current.state.deploymentOrder).toEqual([0, 1])
    })

    it('preserves multiple state updates', () => {
      const { result } = renderHook(() => useTrackerState(defaultInitialEquipment, defaultInitialDeployment))

      act(() => {
        result.current.setDeploymentOrder([0])
        result.current.updateCurrentSkillCount('2', 1, 4)
        result.current.togglePackDone(2, 'themePack3', ['giftX'])
      })

      expect(result.current.state.deploymentOrder).toEqual([0])
      expect(result.current.state.currentSkillCounts['2'][1]).toBe(4)
      expect(result.current.state.doneMarks[2]?.has('themePack3')).toBe(true)
    })
  })
})
