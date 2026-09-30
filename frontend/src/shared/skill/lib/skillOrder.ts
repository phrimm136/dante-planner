import { EGO_SKILL_TYPES, SKILL_DEF_TYPE_ATTACK } from '@/shared/gameData'
import type { SkillId } from '@/shared/gameData'
import type { SkillEntry, SkillMap } from '@/shared/gameData'

export interface OrderedSkill {
  id: SkillId
  entry: SkillEntry
}

type SkillComparator = (a: OrderedSkill, b: OrderedSkill) => number

function byId(a: OrderedSkill, b: OrderedSkill): number {
  return Number(a.id) - Number(b.id)
}

function orderSkills(skills: SkillMap, compare: SkillComparator): OrderedSkill[] {
  const ordered = (Object.entries(skills) as [SkillId, SkillEntry][]).map(([id, entry]) => ({
    id,
    entry,
  }))
  return ordered.sort((a, b) => compare(a, b) || byId(a, b))
}

function defenseRank(skill: OrderedSkill): number {
  return skill.entry.levels[0].defType === SKILL_DEF_TYPE_ATTACK ? 0 : 1
}

const EGO_SKILL_TYPE_ORDER: readonly string[] = [EGO_SKILL_TYPES.AWAKEN, EGO_SKILL_TYPES.EROSION]

function egoSkillTypeRank(skill: OrderedSkill): number {
  const rank = EGO_SKILL_TYPE_ORDER.indexOf(skill.entry.skillType)
  return rank === -1 ? EGO_SKILL_TYPE_ORDER.length : rank
}

export function orderIdentitySkills(skills: SkillMap): OrderedSkill[] {
  return orderSkills(
    skills,
    (a, b) => defenseRank(a) - defenseRank(b) || a.entry.skillTier - b.entry.skillTier,
  )
}

export function orderEgoSkills(skills: SkillMap): OrderedSkill[] {
  return orderSkills(skills, (a, b) => egoSkillTypeRank(a) - egoSkillTypeRank(b))
}
