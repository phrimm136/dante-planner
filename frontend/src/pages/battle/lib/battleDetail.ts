import { ATK_TYPES } from '@/shared/gameData'
import type { SkillEntry, SkillId } from '@/shared/gameData'
import type {
  BattleEngagement,
  BattlePart,
  BattlePhase,
  IdentityShapedPassives,
  PassiveImportanceMap,
  ResistInfo,
} from '../schemas/BattleDetailSchemas'
import { NEUTRAL_RESISTANCE, type BattleCase } from './battleVocabulary'

export const ALL_PARTS = 'all'

export type PartSelection = typeof ALL_PARTS | number

export interface StatGrowth {
  defaultStat: number
  incrementByLevel: number
}

export interface UnitStatus {
  hp: StatGrowth | undefined
  defCorrection: number | undefined
  minSpeedList: readonly number[] | undefined
  maxSpeedList: readonly number[] | undefined
  breakSection: { sectionList: number[] } | undefined
  resistInfo: ResistInfo | undefined
}

export interface Resistances {
  slash: number
  pierce: number
  blunt: number
  attributes: Record<string, number>
}

export interface OrderedBattleSkill {
  id: SkillId
  entry: SkillEntry
}

export function statAtLevel(growth: StatGrowth, level: number): number {
  return Math.floor(growth.defaultStat + growth.incrementByLevel * level)
}

export function defenseAtLevel(level: number, defCorrection: number): number {
  return Math.max(1, level + defCorrection)
}

export function engagementLevels(
  engagements: readonly BattleEngagement[],
  fallback: number | undefined,
): number[] {
  const levels = new Set<number>()
  for (const engagement of engagements) {
    if (engagement.level !== null) levels.add(engagement.level)
  }
  if (levels.size === 0 && fallback !== undefined) levels.add(fallback)
  return [...levels].sort((a, b) => a - b)
}

export function defaultEngagementLevel(
  engagements: readonly BattleEngagement[],
  arrivalCases: ReadonlySet<BattleCase>,
  fallback: number | undefined,
): number | undefined {
  const leveled = engagements.filter((engagement) => engagement.level !== null)
  const arrived = leveled.find((engagement) => arrivalCases.has(engagement.case))
  return (arrived ?? leveled[0])?.level ?? fallback
}

export function selectorParts(phase: BattlePhase): BattlePart[] {
  const parts = phase.parts ?? []
  return parts.length >= 2 ? parts : []
}

export function selectedPart(phase: BattlePhase, selection: PartSelection): BattlePart | undefined {
  if (selection === ALL_PARTS) return undefined
  return selectorParts(phase).find((part) => part.id === selection)
}

export function visibleSkills(
  phase: BattlePhase,
  part: BattlePart | undefined,
): OrderedBattleSkill[] {
  const linked = part === undefined ? undefined : new Set(part.skillIds.map(String))
  return (Object.entries(phase.skills) as [SkillId, SkillEntry][])
    .filter(([id]) => linked === undefined || linked.has(id))
    .sort(([a], [b]) => Number(a) - Number(b))
    .map(([id, entry]) => ({ id, entry }))
}

export function isIdentityShaped(
  passives: BattlePhase['passives'],
): passives is IdentityShapedPassives {
  return 'battlePassiveList' in passives
}

function importanceEntries(passives: PassiveImportanceMap): [string, number][] {
  return Object.entries(passives).map(([id, entry]) => [id, entry.importanceLevel])
}

export function visiblePassives(
  phase: BattlePhase,
  part: BattlePart | undefined,
): [id: string, importanceLevel: number][] {
  if (part !== undefined) {
    const importance = new Map(importanceEntries(part.passives))
    return part.passiveIds.map((id) => [String(id), importance.get(String(id)) ?? 0])
  }
  const own = isIdentityShaped(phase.passives) ? [] : importanceEntries(phase.passives)
  const seen = new Set(own.map(([id]) => id))
  const fromParts: [string, number][] = []
  for (const each of phase.parts ?? []) {
    for (const [id, importance] of importanceEntries(each.passives)) {
      if (seen.has(id)) continue
      seen.add(id)
      fromParts.push([id, importance])
    }
  }
  return [...own, ...fromParts]
}

export function phaseStatus(phase: BattlePhase): UnitStatus {
  return {
    hp: phase.hp,
    defCorrection: phase.defCorrection,
    minSpeedList: phase.minSpeedList,
    maxSpeedList: phase.maxSpeedList,
    breakSection: phase.breakSection,
    resistInfo: phase.resistInfo,
  }
}

export function partStatus(part: BattlePart): UnitStatus {
  return {
    hp: part.hp,
    defCorrection: part.defCorrection,
    minSpeedList: part.minSpeedList,
    maxSpeedList: part.maxSpeedList,
    breakSection: part.breakSection,
    resistInfo: part.resistInfo,
  }
}

const [SLASH, PENETRATE, HIT] = ATK_TYPES

export function resistances(info: ResistInfo): Resistances {
  const attack = new Map(info.atkResistList.map(({ type, value }) => [type, value]))
  return {
    slash: attack.get(SLASH) ?? NEUTRAL_RESISTANCE,
    pierce: attack.get(PENETRATE) ?? NEUTRAL_RESISTANCE,
    blunt: attack.get(HIT) ?? NEUTRAL_RESISTANCE,
    attributes: Object.fromEntries(
      info.attributeResistList.map(({ type, value }) => [type, value]),
    ),
  }
}

export function speedAtTier(list: readonly number[] | undefined, tier: number): number | undefined {
  if (list === undefined) return undefined
  return list[tier - 1] ?? list[list.length - 1]
}

export function phaseTier(phase: BattlePhase, uptie: number | undefined): number {
  if (uptie !== undefined) return uptie
  const skillTiers = Object.values(phase.skills).flatMap((entry) =>
    entry.levels.map((level) => level.level),
  )
  const passiveTiers = isIdentityShaped(phase.passives)
    ? [phase.passives.battlePassiveList.length, phase.passives.supportPassiveList.length]
    : []
  return Math.max(1, ...skillTiers, ...passiveTiers)
}

export function sanityConditionIds(
  entries: readonly { conditionIDList?: { conditionID: string }[] | undefined }[],
): string[] {
  return entries.flatMap((entry) =>
    (entry.conditionIDList ?? []).map((condition) => condition.conditionID),
  )
}
