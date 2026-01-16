import { renderHook, act } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { useTrackerState } from './useTrackerState'
import { DEFAULT_SKILL_EA, SINNERS } from '@/lib/constants'

describe('useTrackerState', () => {
  describe('Initial State', () => {
    it('initializes with default skill EA values (3/2/1)', () => {
      const { result } = renderHook(() => useTrackerState())

      expect(result.current.state.currentSkillCounts['YiSang']).toEqual({
        0: DEFAULT_SKILL_EA[0],
        1: DEFAULT_SKILL_EA[1],
        2: DEFAULT_SKILL_EA[2],
      })
    })

    it('initializes with empty deployment order', () => {
      const { result } = renderHook(() => useTrackerState())

      expect(result.current.state.deploymentOrder).toEqual([])
    })

    it('initializes with empty done marks', () => {
      const { result } = renderHook(() => useTrackerState())

      expect(result.current.state.doneMarks).toEqual({})
    })

    it('initializes with null hovered theme pack', () => {
      const { result } = renderHook(() => useTrackerState())

      expect(result.current.state.hoveredThemePack).toBeNull()
    })

    it('initializes skill counts for all 12 sinners', () => {
      const { result } = renderHook(() => useTrackerState())

      SINNERS.forEach((sinner) => {
        expect(result.current.state.currentSkillCounts[sinner]).toBeDefined()
      })
    })
  })

  describe('Deployment Order', () => {
    it('updates deployment order', () => {
      const { result } = renderHook(() => useTrackerState())
      const newOrder = ['YiSang', 'Faust', 'DonQuixote']

      act(() => {
        result.current.setDeploymentOrder(newOrder)
      })

      expect(result.current.state.deploymentOrder).toEqual(newOrder)
    })

    it('preserves deployment order across multiple updates', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.setDeploymentOrder(['YiSang', 'Faust'])
      })

      act(() => {
        result.current.setDeploymentOrder(['Faust', 'YiSang', 'DonQuixote'])
      })

      expect(result.current.state.deploymentOrder).toEqual(['Faust', 'YiSang', 'DonQuixote'])
    })
  })

  describe('Current Skill Counts', () => {
    it('updates a single skill count', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.updateCurrentSkillCount('YiSang', 0, 5)
      })

      expect(result.current.state.currentSkillCounts['YiSang'][0]).toBe(5)
    })

    it('preserves other skill counts when updating one', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.updateCurrentSkillCount('YiSang', 0, 5)
      })

      expect(result.current.state.currentSkillCounts['YiSang'][1]).toBe(DEFAULT_SKILL_EA[1])
      expect(result.current.state.currentSkillCounts['YiSang'][2]).toBe(DEFAULT_SKILL_EA[2])
    })

    it('updates skill counts for different sinners independently', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.updateCurrentSkillCount('YiSang', 0, 5)
        result.current.updateCurrentSkillCount('Faust', 1, 4)
      })

      expect(result.current.state.currentSkillCounts['YiSang'][0]).toBe(5)
      expect(result.current.state.currentSkillCounts['Faust'][1]).toBe(4)
    })

    it('allows skill count to be set to 0', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.updateCurrentSkillCount('YiSang', 0, 0)
      })

      expect(result.current.state.currentSkillCounts['YiSang'][0]).toBe(0)
    })
  })

  describe('Done Marks', () => {
    it('adds a done mark for a theme pack', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.toggleDoneMark(0, 'themePack1')
      })

      expect(result.current.state.doneMarks[0]?.has('themePack1')).toBe(true)
    })

    it('removes a done mark when toggled again', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.toggleDoneMark(0, 'themePack1')
      })

      act(() => {
        result.current.toggleDoneMark(0, 'themePack1')
      })

      expect(result.current.state.doneMarks[0]?.has('themePack1')).toBe(false)
    })

    it('handles multiple done marks on same floor', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.toggleDoneMark(0, 'themePack1')
        result.current.toggleDoneMark(0, 'themePack2')
      })

      expect(result.current.state.doneMarks[0]?.has('themePack1')).toBe(true)
      expect(result.current.state.doneMarks[0]?.has('themePack2')).toBe(true)
    })

    it('handles done marks on different floors independently', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.toggleDoneMark(0, 'themePack1')
        result.current.toggleDoneMark(1, 'themePack2')
      })

      expect(result.current.state.doneMarks[0]?.has('themePack1')).toBe(true)
      expect(result.current.state.doneMarks[1]?.has('themePack2')).toBe(true)
      expect(result.current.state.doneMarks[0]?.has('themePack2')).toBe(false)
    })
  })

  describe('Hover State', () => {
    it('sets hovered theme pack', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.setHoveredThemePack({ floorIndex: 0, themePackId: 'themePack1' })
      })

      expect(result.current.state.hoveredThemePack).toEqual({
        floorIndex: 0,
        themePackId: 'themePack1',
      })
    })

    it('clears hovered theme pack', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.setHoveredThemePack({ floorIndex: 0, themePackId: 'themePack1' })
      })

      act(() => {
        result.current.setHoveredThemePack(null)
      })

      expect(result.current.state.hoveredThemePack).toBeNull()
    })
  })

  describe('Reset State', () => {
    it('resets all state to defaults', () => {
      const { result } = renderHook(() => useTrackerState())

      // Make changes
      act(() => {
        result.current.setDeploymentOrder(['YiSang', 'Faust'])
        result.current.updateCurrentSkillCount('YiSang', 0, 5)
        result.current.toggleDoneMark(0, 'themePack1')
        result.current.setHoveredThemePack({ floorIndex: 0, themePackId: 'themePack1' })
      })

      // Reset
      act(() => {
        result.current.resetState()
      })

      // Verify reset
      expect(result.current.state.deploymentOrder).toEqual([])
      expect(result.current.state.currentSkillCounts['YiSang'][0]).toBe(DEFAULT_SKILL_EA[0])
      expect(result.current.state.doneMarks).toEqual({})
      expect(result.current.state.hoveredThemePack).toBeNull()
    })
  })

  describe('State Preservation', () => {
    it('preserves state across re-renders', () => {
      const { result, rerender } = renderHook(() => useTrackerState())

      act(() => {
        result.current.setDeploymentOrder(['YiSang', 'Faust'])
      })

      rerender()

      expect(result.current.state.deploymentOrder).toEqual(['YiSang', 'Faust'])
    })

    it('preserves multiple state updates', () => {
      const { result } = renderHook(() => useTrackerState())

      act(() => {
        result.current.setDeploymentOrder(['YiSang'])
        result.current.updateCurrentSkillCount('Faust', 1, 4)
        result.current.toggleDoneMark(2, 'themePack3')
      })

      expect(result.current.state.deploymentOrder).toEqual(['YiSang'])
      expect(result.current.state.currentSkillCounts['Faust'][1]).toBe(4)
      expect(result.current.state.doneMarks[2]?.has('themePack3')).toBe(true)
    })
  })
})
