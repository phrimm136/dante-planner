import { ATK_TYPES } from '@/shared/gameData'
import type { SkillDescEntry, SkillId } from '@/shared/gameData'
import type { Shown } from '@/shared/unitStatus'
import type {
  BattleEntityI18n,
  BattlePart,
  BattlePhase,
  BattlePhaseI18n,
  BattleSkillAcquire,
  BattleSkillEntry,
  BuffGrant,
  EngagementRow,
  IdentityShapedPassives,
  PassiveImportanceMap,
  ResistInfo,
  StartBuff,
  UnitEntry,
} from '../schemas/BattleDetailSchemas'
import type { SkillLevelAdders } from './battleGrowth'
import { NEUTRAL_RESISTANCE, SKILL_LEVEL_ADDER_BY_DEF_TYPE } from './battleVocabulary'

export const ALL_PARTS = 'all'

const SHADOW_ENEMY = 'SHADOW_ENEMY'

export type PartSelection = typeof ALL_PARTS | number

export interface StatGrowth {
  defaultStat: number
  incrementByLevel: number
}

export interface StatusView {
  hp: number | undefined
  speed: Shown<{ min: number; max: number }> | undefined
  defense: Shown<{ level: number; correction: number }> | undefined
  physical: Shown<{ slash: number; pierce: number; blunt: number }> | undefined
  sins: Shown<Record<string, number>> | undefined
  breakSection: number[] | undefined
}

interface Resistances {
  slash: number
  pierce: number
  blunt: number
  attributes: Record<string, number>
}

export interface OrderedBattleSkill {
  id: SkillId
  entry: BattleSkillEntry
}

export interface AcquireJoiners {
  and: string
  or: string
}

export function statAtLevel(growth: StatGrowth, level: number): number {
  return Math.floor(growth.defaultStat + growth.incrementByLevel * level)
}

function defenseAtLevel(level: number, defCorrection: number): number {
  return Math.max(1, level + defCorrection)
}

export function skillLevelAdder(defType: string, adders: SkillLevelAdders | undefined): number {
  const adder = SKILL_LEVEL_ADDER_BY_DEF_TYPE[defType]
  return adders === undefined || adder === undefined ? 0 : adders[adder]
}

export function skillLevelName(
  descs: readonly SkillDescEntry[],
  level: number,
  skillName: string,
): string {
  return descs[level - 1]?.name ?? skillName
}

const LESSON_GRADE_CONDITION = /^ProjectGS\w+LessonGrade$/

type ConditionWords = Readonly<Record<string, string>> | undefined

function conditionText(
  key: string,
  value: number,
  conditionNames: ConditionWords,
  conditionGrades: ConditionWords,
): string {
  const grade = LESSON_GRADE_CONDITION.test(key) ? conditionGrades?.[String(value)] : undefined
  return `${conditionNames?.[key] ?? key} ${grade ?? String(value)}`
}

export function acquireConditionText(
  acquire: BattleSkillAcquire,
  conditionNames: ConditionWords,
  joiners: AcquireJoiners,
  conditionGrades?: ConditionWords,
): string {
  return acquire
    .map(({ condition }) =>
      condition
        .map(({ condition: key, value }) =>
          conditionText(key, value, conditionNames, conditionGrades),
        )
        .join(joiners.and),
    )
    .join(joiners.or)
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

export function displayedUnit<Kit>(
  group: { unitOrder: [UnitEntry<Kit>, ...UnitEntry<Kit>[]] },
  groupId: string,
  row: (EngagementRow & { kit: Kit }) | undefined,
): UnitEntry<Kit> {
  if (row !== undefined) return { unitId: String(row.unit), kit: row.kit }
  return group.unitOrder.find((entry) => entry.unitId === groupId) ?? group.unitOrder[0]
}

export function headerMapName(
  row: EngagementRow | undefined,
  phase: BattlePhase,
): string | undefined {
  return row?.phaseMaps?.[String(phase.id)] ?? row?.mapName
}

export function rowParts(phase: BattlePhase, row: EngagementRow | undefined): BattlePart[] {
  return (phase.parts ?? []).filter(
    (part) => part.generatedBy === undefined || (row?.rowParts.includes(part.id) ?? false),
  )
}

export function selectedPart(
  parts: readonly BattlePart[],
  selection: PartSelection,
): BattlePart | undefined {
  if (selection === ALL_PARTS) return undefined
  return parts.find((part) => part.id === selection)
}

export function visibleSkills(
  phase: BattlePhase,
  parts: readonly BattlePart[],
  part: BattlePart | undefined,
  row: EngagementRow | undefined,
): OrderedBattleSkill[] {
  const linked = part === undefined ? undefined : new Set(part.skillIds.map(String))
  const unlocked = new Set(row?.unlockedSkillIds ?? [])
  const locked = new Set((phase.gatedSkillIds ?? []).filter((id) => !unlocked.has(id)).map(String))
  const present = new Set(parts.flatMap((each) => each.skillIds.map(String)))
  const absentOnly = new Set(
    (phase.parts ?? [])
      .filter((each) => !parts.includes(each))
      .flatMap((each) => each.skillIds.map(String))
      .filter((id) => !present.has(id)),
  )
  return (Object.entries(phase.skills) as [SkillId, BattleSkillEntry][])
    .filter(
      ([id]) => !absentOnly.has(id) && !locked.has(id) && (linked === undefined || linked.has(id)),
    )
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

function isAbnormalityWhole(
  phase: BattlePhase,
  part: BattlePart | undefined,
  parts: readonly BattlePart[],
): boolean {
  return (
    part === undefined &&
    phase.ownerType === 'ABNORMALITY' &&
    parts.length >= 1 &&
    !(phase.unitKeywordList?.includes(SHADOW_ENEMY) ?? false)
  )
}

export function statusView(
  phase: BattlePhase,
  part: BattlePart | undefined,
  parts: readonly BattlePart[],
  level: number | undefined,
  tier: number,
): StatusView {
  const unit = part ?? phase
  const hp = unit.hp === undefined || level === undefined ? undefined : statAtLevel(unit.hp, level)
  if (isAbnormalityWhole(phase, part, parts)) {
    const blank = { kind: 'blank' } as const
    return {
      hp,
      speed: blank,
      defense: blank,
      physical: blank,
      sins: blank,
      breakSection: undefined,
    }
  }
  const min = speedAtTier(unit.minSpeedList, tier)
  const max = speedAtTier(unit.maxSpeedList, tier)
  const correction = unit.defCorrection
  const resist = unit.resistInfo === undefined ? undefined : resistances(unit.resistInfo)
  return {
    hp,
    speed:
      min === undefined || max === undefined ? undefined : { kind: 'shown', value: { min, max } },
    defense:
      level === undefined || correction === undefined
        ? undefined
        : { kind: 'shown', value: { level: defenseAtLevel(level, correction), correction } },
    physical:
      resist === undefined
        ? undefined
        : {
            kind: 'shown',
            value: { slash: resist.slash, pierce: resist.pierce, blunt: resist.blunt },
          },
    sins: resist === undefined ? undefined : { kind: 'shown', value: resist.attributes },
    breakSection: unit.breakSection?.sectionList,
  }
}

const [SLASH, PENETRATE, HIT] = ATK_TYPES

function resistances(info: ResistInfo): Resistances {
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

function speedAtTier(list: readonly number[] | undefined, tier: number): number | undefined {
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

export function rowContentKey(row: EngagementRow): string {
  const keywords = stageEffectGrants(row)
    .map((grant) => grant.keywordId)
    .sort()
  const parts = [...row.rowParts].sort((a, b) => a - b)
  return JSON.stringify([row.unit, row.level, parts, keywords])
}

export type StageEffectGrant = BuffGrant & { keywordId: string }

export interface StageEffectEntry {
  keywordId: string
  stack: number | undefined
  side: 'enemy' | 'ally' | undefined
}

export function stageEffectEntries(
  startBuffs: readonly StartBuff[],
  grants: readonly StageEffectGrant[],
): StageEffectEntry[] {
  const fromForm = startBuffs.map(
    (buff): StageEffectEntry => ({ keywordId: buff.keywordId, stack: buff.stack, side: undefined }),
  )
  const formKeywords = new Set(fromForm.map((entry) => entry.keywordId))
  const fromRow = grants
    .filter((grant) => !formKeywords.has(grant.keywordId))
    .map(
      (grant): StageEffectEntry => ({
        keywordId: grant.keywordId,
        stack: grant.stack,
        side: grant.target === 'unknown' ? undefined : grant.target,
      }),
    )
  return [...fromForm, ...fromRow]
}

export function stageEffectGrants(row: EngagementRow): StageEffectGrant[] {
  const seen = new Set<string>()
  const grants: StageEffectGrant[] = []
  for (const grant of row.facts.buffGrants) {
    const { keywordId } = grant
    if (grant.derivable || keywordId === null || seen.has(keywordId)) continue
    seen.add(keywordId)
    grants.push({ ...grant, keywordId })
  }
  return grants
}
