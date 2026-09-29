import { EGO_SKILL_TYPES, SKILL_DEF_TYPE_ATTACK } from '@/shared/gameData'
import type { SkillId } from '@/shared/gameData'
import type { SkillEntry, SkillMap } from '@/shared/gameData'

interface SkillRef {
  id: SkillId
  entry: SkillEntry
}

export interface OrderedSkill extends SkillRef {
  group: string
}

type SkillComparator = (a: SkillRef, b: SkillRef) => number

function byId(a: SkillRef, b: SkillRef): number {
  return Number(a.id) - Number(b.id)
}

function orderSkills(
  skills: SkillMap,
  compare: SkillComparator,
  groupOf: (skill: SkillRef) => string,
): OrderedSkill[] {
  const ordered = (Object.entries(skills) as [SkillId, SkillEntry][]).map(([id, entry]) => ({
    id,
    entry,
  }))
  return ordered
    .sort((a, b) => compare(a, b) || byId(a, b))
    .map(({ id, entry }) => ({ id, entry, group: groupOf({ id, entry }) }))
}

const DEFENSE_GROUP = 'defense'

function defenseRank(skill: SkillRef): number {
  return skill.entry.levels[0].defType === SKILL_DEF_TYPE_ATTACK ? 0 : 1
}

const EGO_SKILL_TYPE_ORDER: readonly string[] = [EGO_SKILL_TYPES.AWAKEN, EGO_SKILL_TYPES.EROSION]

function egoSkillTypeRank(skill: SkillRef): number {
  const rank = EGO_SKILL_TYPE_ORDER.indexOf(skill.entry.skillType)
  return rank === -1 ? EGO_SKILL_TYPE_ORDER.length : rank
}

export function orderIdentitySkills(skills: SkillMap): OrderedSkill[] {
  return orderSkills(
    skills,
    (a, b) => defenseRank(a) - defenseRank(b) || a.entry.skillTier - b.entry.skillTier,
    (skill) => (defenseRank(skill) === 0 ? String(skill.entry.skillTier) : DEFENSE_GROUP),
  )
}

export function orderEgoSkills(skills: SkillMap): OrderedSkill[] {
  return orderSkills(
    skills,
    (a, b) => egoSkillTypeRank(a) - egoSkillTypeRank(b),
    (skill) => skill.entry.skillType,
  )
}
