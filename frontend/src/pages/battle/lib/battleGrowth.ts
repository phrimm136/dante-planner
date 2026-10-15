import { ATK_TYPES } from '@/shared/gameData'
import type {
  BattleGrowth,
  BattlePhase,
  BattleSkillEntry,
  BattleUnlock,
  GrowthCondition,
  IdentityShapedPassives,
  ResistInfo,
} from '../schemas/BattleDetailSchemas'
import { GROWTH_CATEGORIES } from './battleVocabulary'

export type GrowthInputs = Readonly<Record<string, number>>

export interface SkillLevelAdders {
  attack: number
  defense: number
}

export interface GrowthState {
  level: number
  hpAdd: number
  speedAdd: number
  skillLevelAdders: SkillLevelAdders
  slashResistAdd: number
}

type PassiveUnlocks = NonNullable<IdentityShapedPassives['unlock']>

const [SLASH] = ATK_TYPES

export function defaultGrowthInputs(growth: BattleGrowth): Record<string, number> {
  return Object.fromEntries(growth.tracks.map((track) => [track, growth.max]))
}

export function clampGrowthInput(value: number, growth: BattleGrowth): number {
  if (!Number.isFinite(value)) return 0
  return Math.min(growth.max, Math.max(0, Math.trunc(value)))
}

export function conditionsHold(
  conditions: readonly GrowthCondition[],
  growth: BattleGrowth,
  inputs: GrowthInputs,
): boolean {
  return conditions.every(
    ({ condition, value }) =>
      !growth.tracks.includes(condition) || (inputs[condition] ?? growth.max) >= value,
  )
}

export function unlockHolds(
  unlock: BattleUnlock | undefined,
  growth: BattleGrowth,
  inputs: GrowthInputs,
): boolean {
  return unlock === undefined || conditionsHold(unlock.condition, growth, inputs)
}

export function acquireHolds(
  acquire: readonly BattleUnlock[] | undefined,
  growth: BattleGrowth,
  inputs: GrowthInputs,
): boolean {
  return (
    acquire === undefined || acquire.some((set) => conditionsHold(set.condition, growth, inputs))
  )
}

export function heldGrowthRows(growth: BattleGrowth, inputs: GrowthInputs): BattleGrowth['rows'] {
  return growth.rows.filter((row) => conditionsHold(row.conditions, growth, inputs))
}

export function growthState(
  growth: BattleGrowth,
  inputs: GrowthInputs,
  baseLevel: number,
): GrowthState {
  const sums = {
    level: baseLevel,
    hpAdd: 0,
    speedAdd: 0,
    skillLevelAdders: { attack: 0, defense: 0 },
    slashResistAdd: 0,
  }
  for (const row of heldGrowthRows(growth, inputs)) {
    switch (row.category) {
      case GROWTH_CATEGORIES.unitLevel:
        sums.level += row.value
        break
      case GROWTH_CATEGORIES.maxHp:
        sums.hpAdd += row.value
        break
      case GROWTH_CATEGORIES.speed:
        sums.speedAdd += row.value
        break
      case GROWTH_CATEGORIES.attack:
        sums.skillLevelAdders.attack += row.value
        break
      case GROWTH_CATEGORIES.defense:
        sums.skillLevelAdders.defense += row.value
        break
      case GROWTH_CATEGORIES.slashResist:
        sums.slashResistAdd += row.floatValue
        break
    }
  }
  return sums
}

export function passiveSlotIds<TId extends string>(
  tiers: readonly (readonly TId[])[],
  unlocks: PassiveUnlocks | undefined,
  growth: BattleGrowth,
  inputs: GrowthInputs,
): (TId | undefined)[] {
  const unlockById = new Map<string, BattleUnlock>()
  for (const byId of Object.values(unlocks ?? {})) {
    for (const [id, unlock] of Object.entries(byId)) unlockById.set(id, unlock)
  }
  const slotCount = Math.max(0, ...tiers.map((tier) => tier.length))
  return Array.from({ length: slotCount }, (_, slot) => {
    for (let tier = tiers.length - 1; tier >= 0; tier -= 1) {
      const id = tiers[tier]?.[slot]
      if (id !== undefined && unlockHolds(unlockById.get(id), growth, inputs)) return id
    }
    return undefined
  })
}

function grownSkill(
  entry: BattleSkillEntry,
  growth: BattleGrowth,
  inputs: GrowthInputs,
): BattleSkillEntry {
  const [first = entry.levels[0], ...rest] = entry.levels.filter((level) =>
    unlockHolds(level.unlock, growth, inputs),
  )
  return { ...entry, levels: [first, ...rest] }
}

function grownResist(info: ResistInfo, slashAdd: number): ResistInfo {
  const hasSlash = info.atkResistList.some((entry) => entry.type === SLASH)
  return {
    ...info,
    atkResistList: hasSlash
      ? info.atkResistList.map((entry) =>
          entry.type === SLASH ? { ...entry, value: entry.value + slashAdd } : entry,
        )
      : info.atkResistList,
  }
}

function grownPassives(
  passives: BattlePhase['passives'],
  growth: BattleGrowth,
  inputs: GrowthInputs,
): BattlePhase['passives'] {
  if (!('battlePassiveList' in passives)) return passives
  const held = (tiers: IdentityShapedPassives['battlePassiveList']) => {
    const slots = passiveSlotIds(tiers, passives.unlock, growth, inputs).filter(
      (id) => id !== undefined,
    )
    return tiers.map(() => slots)
  }
  return {
    ...passives,
    battlePassiveList: held(passives.battlePassiveList),
    supportPassiveList: held(passives.supportPassiveList),
  }
}

export function grownPhase(phase: BattlePhase, inputs: GrowthInputs): BattlePhase {
  const { growth } = phase
  if (growth === undefined) return phase
  const state = growthState(growth, inputs, 0)
  const addSpeed = (list: number[] | undefined) => list?.map((speed) => speed + state.speedAdd)
  return {
    ...phase,
    hp: { ...phase.hp, defaultStat: phase.hp.defaultStat + state.hpAdd },
    minSpeedList: addSpeed(phase.minSpeedList),
    maxSpeedList: addSpeed(phase.maxSpeedList),
    resistInfo:
      phase.resistInfo === undefined
        ? undefined
        : grownResist(phase.resistInfo, state.slashResistAdd),
    skills: Object.fromEntries(
      Object.entries(phase.skills)
        .filter(([, entry]) => acquireHolds(entry.acquire, growth, inputs))
        .map(([id, entry]) => [id, grownSkill(entry, growth, inputs)]),
    ),
    passives: grownPassives(phase.passives, growth, inputs),
  }
}
