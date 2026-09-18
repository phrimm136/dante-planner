import { useState } from 'react'
import { DEFAULT_SKILL_EA, SINNERS } from '@/shared/gameData'
import type { OffensiveSkillSlot } from '@/shared/gameData'
import type { SinnerEquipment } from '../types/DeckTypes'

export interface TrackerState {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  currentSkillCounts: Record<string, Record<OffensiveSkillSlot, number>>
  doneMarks: Record<number, Set<string>>
  egoGiftDoneMarks: Set<string>
}

export interface TrackerStateResult {
  state: TrackerState
  setEquipment: React.Dispatch<React.SetStateAction<Record<string, SinnerEquipment>>>
  setDeploymentOrder: React.Dispatch<React.SetStateAction<number[]>>
  setCurrentSkillCounts: React.Dispatch<
    React.SetStateAction<Record<string, Record<OffensiveSkillSlot, number>>>
  >
  updateCurrentSkillCount: (sinnerId: string, skillSlot: OffensiveSkillSlot, count: number) => void
  toggleEgoGiftDoneMark: (encodedId: string) => void
  togglePackDone: (floorIndex: number, themePackId: string, giftIds: string[]) => void
  resetState: (
    initialEquipment: Record<string, SinnerEquipment>,
    initialDeployment: number[],
  ) => void
}

interface DoneMarkState {
  packs: Record<number, Set<string>>
  gifts: Set<string>
}

function createInitialDoneMarks(): DoneMarkState {
  return { packs: {}, gifts: new Set() }
}

function defaultSkillCounts(): Record<OffensiveSkillSlot, number> {
  return {
    0: DEFAULT_SKILL_EA[0],
    1: DEFAULT_SKILL_EA[1],
    2: DEFAULT_SKILL_EA[2],
  }
}

function createInitialSkillCounts(): Record<string, Record<OffensiveSkillSlot, number>> {
  const currentSkillCounts: Record<string, Record<OffensiveSkillSlot, number>> = {}

  for (let i = 0; i < SINNERS.length; i++) {
    currentSkillCounts[String(i + 1)] = defaultSkillCounts()
  }

  return currentSkillCounts
}

export function useTrackerState(
  initialEquipment: Record<string, SinnerEquipment>,
  initialDeployment: number[],
): TrackerStateResult {
  const [equipment, setEquipment] = useState<Record<string, SinnerEquipment>>(initialEquipment)
  const [deploymentOrder, setDeploymentOrder] = useState<number[]>(initialDeployment)
  const [currentSkillCounts, setCurrentSkillCounts] =
    useState<Record<string, Record<OffensiveSkillSlot, number>>>(createInitialSkillCounts)
  const [doneMarkState, setDoneMarkState] = useState<DoneMarkState>(createInitialDoneMarks)

  const updateCurrentSkillCount = (
    sinnerId: string,
    skillSlot: OffensiveSkillSlot,
    count: number,
  ) => {
    setCurrentSkillCounts((prev) => {
      const sinnerCounts = prev[sinnerId] ?? defaultSkillCounts()
      return {
        ...prev,
        [sinnerId]: { ...sinnerCounts, [skillSlot]: count },
      }
    })
  }

  const toggleEgoGiftDoneMark = (encodedId: string) => {
    setDoneMarkState((prev) => {
      const gifts = new Set(prev.gifts)

      if (gifts.has(encodedId)) {
        gifts.delete(encodedId)
      } else {
        gifts.add(encodedId)
      }

      return { ...prev, gifts }
    })
  }

  const togglePackDone = (floorIndex: number, themePackId: string, giftIds: string[]) => {
    setDoneMarkState((prev) => {
      const floorMarks = new Set(prev.packs[floorIndex] || [])
      const wasDone = floorMarks.has(themePackId)

      if (wasDone) {
        floorMarks.delete(themePackId)
      } else {
        floorMarks.add(themePackId)
      }

      const gifts = new Set(prev.gifts)
      for (const id of giftIds) {
        if (wasDone) {
          gifts.delete(id)
        } else {
          gifts.add(id)
        }
      }

      return { packs: { ...prev.packs, [floorIndex]: floorMarks }, gifts }
    })
  }

  const resetState = (
    resetEquipment: Record<string, SinnerEquipment>,
    resetDeployment: number[],
  ) => {
    setEquipment(resetEquipment)
    setDeploymentOrder(resetDeployment)
    setCurrentSkillCounts(createInitialSkillCounts())
    setDoneMarkState(createInitialDoneMarks())
  }

  return {
    state: {
      equipment,
      deploymentOrder,
      currentSkillCounts,
      doneMarks: doneMarkState.packs,
      egoGiftDoneMarks: doneMarkState.gifts,
    },
    setEquipment,
    setDeploymentOrder,
    setCurrentSkillCounts,
    updateCurrentSkillCount,
    toggleEgoGiftDoneMark,
    togglePackDone,
    resetState,
  }
}
