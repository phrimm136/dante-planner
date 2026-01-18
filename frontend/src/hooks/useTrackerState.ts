import { useState, useCallback } from 'react'
import { DEFAULT_SKILL_EA, SINNERS } from '@/lib/constants'
import type { OffensiveSkillSlot } from '@/lib/constants'
import type { SinnerEquipment } from '@/types/DeckTypes'

/**
 * Tracker state for planner viewer tracker mode
 * Session-only state, resets on unmount/refresh
 *
 * Reset behavior:
 * - equipment: Restored to planner's initial equipment
 * - deploymentOrder: Restored to planner's initial deployment
 * - currentSkillCounts: Reset to DEFAULT_SKILL_EA (3/2/1)
 * - doneMarks: Cleared (empty object)
 */
export interface TrackerState {
  /** Temporary equipment (can change during gameplay) */
  equipment: Record<string, SinnerEquipment>
  /** Temporary deployment order (sinner indices 0-11). User can modify to track actual gameplay order. */
  deploymentOrder: number[]
  /** Current skill counts (sinnerID -> skillSlot -> count) */
  currentSkillCounts: Record<string, Record<OffensiveSkillSlot, number>>
  /** Done marks per floor (floorIndex 0-based -> Set<themePackID>) */
  doneMarks: Record<number, Set<string>>
}

/**
 * Return type for useTrackerState hook
 */
export interface TrackerStateResult {
  /** Current tracker state */
  state: TrackerState
  /** Set equipment */
  setEquipment: React.Dispatch<React.SetStateAction<Record<string, SinnerEquipment>>>
  /** Set deployment order (user can set to empty array to track cleared deployment) */
  setDeploymentOrder: React.Dispatch<React.SetStateAction<number[]>>
  /** Set current skill counts */
  setCurrentSkillCounts: React.Dispatch<React.SetStateAction<Record<string, Record<OffensiveSkillSlot, number>>>>
  /** Update a single skill count */
  updateCurrentSkillCount: (sinnerId: string, skillSlot: OffensiveSkillSlot, count: number) => void
  /** Toggle done mark for a theme pack (floorIndex is 0-based) */
  toggleDoneMark: (floorIndex: number, themePackId: string) => void
  /** Reset all state to initial values (equipment and deployment from planner, skills to default, done marks cleared) */
  resetState: (initialEquipment: Record<string, SinnerEquipment>, initialDeployment: number[]) => void
}

/**
 * Create initial skill counts
 * Default EA values: 3/2/1 for skill slots 0/1/2
 * Keys are numeric strings ("1", "2", ...) matching equipment/plannedEAState format
 */
function createInitialSkillCounts(): Record<string, Record<OffensiveSkillSlot, number>> {
  const currentSkillCounts: Record<string, Record<OffensiveSkillSlot, number>> = {}

  for (let i = 0; i < SINNERS.length; i++) {
    const sinnerCode = String(i + 1)
    currentSkillCounts[sinnerCode] = {
      0: DEFAULT_SKILL_EA[0],
      1: DEFAULT_SKILL_EA[1],
      2: DEFAULT_SKILL_EA[2],
    }
  }

  return currentSkillCounts
}

/**
 * Session state management for tracker mode
 *
 * Features:
 * - Deployment order: Temporary changes to deck deployment
 * - Current skill counts: User-tracked during MD run (default: 3/2/1)
 * - Done marks: Theme packs marked as done
 * - Hover state: Which theme pack is currently hovered
 *
 * State behavior:
 * - Preserved between guide ↔ tracker mode switches
 * - Resets only on unmount/refresh, NOT on mode switch
 * - Session-only (no IndexedDB, no localStorage, no server sync)
 *
 * @example
 * ```tsx
 * function TrackerModeViewer({ planner }) {
 *   const {
 *     state,
 *     setDeploymentOrder,
 *     updateCurrentSkillCount,
 *     toggleDoneMark,
 *     setHoveredThemePack,
 *     resetState,
 *   } = useTrackerState()
 *
 *   return (
 *     <div>
 *       <DeckTrackerPanel
 *         deploymentOrder={state.deploymentOrder}
 *         onDeploymentChange={setDeploymentOrder}
 *       />
 *       <SkillTrackerPanel
 *         currentCounts={state.currentSkillCounts}
 *         onCountChange={updateCurrentSkillCount}
 *       />
 *     </div>
 *   )
 * }
 * ```
 */
export function useTrackerState(
  initialEquipment: Record<string, SinnerEquipment>,
  initialDeployment: number[]
): TrackerStateResult {
  const [equipment, setEquipment] = useState<Record<string, SinnerEquipment>>(initialEquipment)
  const [deploymentOrder, setDeploymentOrder] = useState<number[]>(initialDeployment)
  const [currentSkillCounts, setCurrentSkillCounts] = useState<Record<string, Record<OffensiveSkillSlot, number>>>(
    createInitialSkillCounts
  )
  const [doneMarks, setDoneMarks] = useState<Record<number, Set<string>>>({})

  const updateCurrentSkillCount = useCallback((
    sinnerId: string,
    skillSlot: OffensiveSkillSlot,
    count: number
  ) => {
    setCurrentSkillCounts((prev) => ({
      ...prev,
      [sinnerId]: {
        ...prev[sinnerId],
        [skillSlot]: count,
      },
    }))
  }, [])

  const toggleDoneMark = useCallback((floorIndex: number, themePackId: string) => {
    setDoneMarks((prev) => {
      const floorMarks = new Set(prev[floorIndex] || [])

      if (floorMarks.has(themePackId)) {
        floorMarks.delete(themePackId)
      } else {
        floorMarks.add(themePackId)
      }

      return {
        ...prev,
        [floorIndex]: floorMarks,
      }
    })
  }, [])

  const resetState = useCallback((resetEquipment: Record<string, SinnerEquipment>, resetDeployment: number[]) => {
    setEquipment(resetEquipment)
    setDeploymentOrder(resetDeployment)
    setCurrentSkillCounts(createInitialSkillCounts())
    setDoneMarks({})
  }, [])

  return {
    state: {
      equipment,
      deploymentOrder,
      currentSkillCounts,
      doneMarks,
    },
    setEquipment,
    setDeploymentOrder,
    setCurrentSkillCounts,
    updateCurrentSkillCount,
    toggleDoneMark,
    resetState,
  }
}
