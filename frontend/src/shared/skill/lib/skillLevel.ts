import { COIN_OPERATOR_SUB } from '@/shared/gameData'
import type { SkillEntry, SkillLevel } from '@/shared/gameData'

export interface SelectedSkillLevel {
  level: SkillLevel
  isLocked: boolean
}

export function selectSkillLevel(levels: SkillEntry['levels'], tier: number): SelectedSkillLevel {
  let selected: SkillLevel | undefined
  let lowest = levels[0]
  for (const candidate of levels) {
    if (candidate.level < lowest.level) lowest = candidate
    if (candidate.level <= tier && (selected === undefined || candidate.level > selected.level)) {
      selected = candidate
    }
  }
  return selected === undefined
    ? { level: lowest, isLocked: true }
    : { level: selected, isLocked: false }
}

export interface SkillCardData {
  attributeType: string
  atkType: string
  defaultValue: number
  scale: number | undefined
  skillLevelCorrection: number
  targetNum: number
  mpUsage: number
}

export function toSkillCardData(entry: SkillEntry, level: SkillLevel): SkillCardData {
  const firstCoin = level.coins[0]
  return {
    attributeType: level.attributeType,
    atkType: level.atkType,
    defaultValue: level.defaultValue,
    scale:
      firstCoin === undefined
        ? undefined
        : firstCoin.operator === COIN_OPERATOR_SUB
          ? -firstCoin.scale
          : firstCoin.scale,
    skillLevelCorrection: level.skillLevelCorrection,
    targetNum: level.targetNum,
    mpUsage: entry.mpUsage,
  }
}

export function skillLevelAt(
  unitLevel: number | undefined,
  correction: number,
): number | undefined {
  return unitLevel === undefined ? undefined : Math.max(1, unitLevel + correction)
}
