import { ATK_TYPES } from '@/shared/gameData'
import type { SkillEntry, SkillId } from '@/shared/gameData'
import type {
  BattleEntityI18n,
  BattlePart,
  BattlePhase,
  BattlePhaseI18n,
  BuffGrant,
  EngagementRow,
  IdentityShapedPassives,
  PassiveImportanceMap,
  ResistInfo,
} from '../schemas/BattleDetailSchemas'
import { NEUTRAL_RESISTANCE } from './battleVocabulary'

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

export function phaseTexts(
  i18n: BattleEntityI18n,
  unitId: string,
  phaseId: string,
): BattlePhaseI18n | undefined {
  return i18n.units[unitId]?.phases?.[phaseId]
}

export function unitName(i18n: BattleEntityI18n, unitId: string, fallback: string): string {
  return i18n.units[unitId]?.name ?? fallback
}

export function rowUnitId(
  unitIds: readonly string[],
  groupId: string,
  row: EngagementRow | undefined,
): string {
  if (row !== undefined) return String(row.unit)
  return unitIds.includes(groupId) ? groupId : (unitIds[0] ?? groupId)
}

export function rowParts(phase: BattlePhase, row: EngagementRow | undefined): BattlePart[] {
  return (phase.parts ?? []).filter(
    (part) => part.generatedBy === undefined || (row?.rowParts.includes(part.id) ?? false),
  )
}

export function selectorParts(parts: readonly BattlePart[]): readonly BattlePart[] {
  return parts.length >= 2 ? parts : []
}

export function selectedPart(
  parts: readonly BattlePart[],
  selection: PartSelection,
): BattlePart | undefined {
  if (selection === ALL_PARTS) return undefined
  return selectorParts(parts).find((part) => part.id === selection)
}

export function visibleSkills(
  phase: BattlePhase,
  parts: readonly BattlePart[],
  part: BattlePart | undefined,
): OrderedBattleSkill[] {
  const linked = part === undefined ? undefined : new Set(part.skillIds.map(String))
  const present = new Set(parts.flatMap((each) => each.skillIds.map(String)))
  const absentOnly = new Set(
    (phase.parts ?? [])
      .filter((each) => !parts.includes(each))
      .flatMap((each) => each.skillIds.map(String))
      .filter((id) => !present.has(id)),
  )
  return (Object.entries(phase.skills) as [SkillId, SkillEntry][])
    .filter(([id]) => !absentOnly.has(id) && (linked === undefined || linked.has(id)))
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

export type PassiveTexts = BattlePhaseI18n['passives']

export function isShownPassive(texts: PassiveTexts, passiveId: string): boolean {
  const text = texts?.[passiveId]
  return text !== undefined && text.hidden !== true
}

export function withShownPassives(
  passives: IdentityShapedPassives,
  texts: PassiveTexts,
): IdentityShapedPassives {
  const shown = (lists: IdentityShapedPassives['battlePassiveList']) =>
    lists.map((list) => list.filter((id) => isShownPassive(texts, id)))
  return {
    ...passives,
    battlePassiveList: shown(passives.battlePassiveList),
    supportPassiveList: shown(passives.supportPassiveList),
  }
}

export function visiblePassives(
  phase: BattlePhase,
  parts: readonly BattlePart[],
  part: BattlePart | undefined,
): [id: string, importanceLevel: number][] {
  if (part !== undefined) {
    const importance = new Map(importanceEntries(part.passives))
    return part.passiveIds.map((id) => [String(id), importance.get(String(id)) ?? 0])
  }
  const own = isIdentityShaped(phase.passives) ? [] : importanceEntries(phase.passives)
  const seen = new Set(own.map(([id]) => id))
  const fromParts: [string, number][] = []
  for (const each of parts) {
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

const KEYWORD_TOKEN = /\[([^\]]+)\]/g

export function phaseTextKeywords(texts: BattlePhaseI18n | undefined): Set<string> {
  const strings = [
    ...Object.values(texts?.skills ?? {}).flatMap((skill) =>
      skill.descs.flatMap((desc) => [desc.desc ?? '', ...(desc.coinDescs ?? [])]),
    ),
    ...Object.values(texts?.passives ?? {}).map((passive) => passive.desc ?? ''),
  ]
  return new Set(
    strings.flatMap((text) => [...text.matchAll(KEYWORD_TOKEN)].map((m) => m[1] ?? '')),
  )
}

export function unitTextKeywords(
  i18n: BattleEntityI18n,
  unitId: string,
  phaseIds: readonly string[],
): Set<string> {
  return new Set(
    phaseIds.flatMap((phaseId) => [...phaseTextKeywords(phaseTexts(i18n, unitId, phaseId))]),
  )
}

export type StageEffectGrant = BuffGrant & { keywordId: string }

export interface OwnSources {
  unitIds: ReadonlySet<number>
  partIds: ReadonlySet<number>
}

export function ownSources(unitId: string, phases: readonly BattlePhase[]): OwnSources {
  return {
    unitIds: new Set([Number(unitId), ...phases.map((phase) => phase.id)]),
    partIds: new Set(phases.flatMap((phase) => (phase.parts ?? []).map((part) => part.id))),
  }
}

export function stageEffectGrants(
  row: EngagementRow,
  own: OwnSources,
  ownKeywords: ReadonlySet<string>,
): StageEffectGrant[] {
  const seen = new Set<string>()
  const grants: StageEffectGrant[] = []
  for (const grant of row.facts.buffGrants) {
    const { keywordId } = grant
    if (keywordId === null || seen.has(keywordId) || ownKeywords.has(keywordId)) continue
    const { kind, unitId, partId } = grant.source
    if (kind === 'passive' && unitId !== undefined && own.unitIds.has(unitId)) continue
    if (kind === 'passive' && partId !== undefined && own.partIds.has(partId)) continue
    seen.add(keywordId)
    grants.push({ ...grant, keywordId })
  }
  return grants
}
