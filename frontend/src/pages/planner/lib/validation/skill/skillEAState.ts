import type { Codes } from '../codes'
import type { ContentPath } from '../contentPath'
import type { SkillPlanValue } from '../contentShape'

const SKILL_EA_TOTAL = 6

export type SkillViolation = {
  code: Codes<'OUT_OF_RANGE_SKILL_TOTAL'>
  path: ContentPath
  total: number
}

export function sinnerSkillViolations(slots: SkillPlanValue, path: ContentPath): SkillViolation[] {
  const total = Object.values(slots).reduce((sum, slotPoints) => sum + slotPoints, 0)
  return total === SKILL_EA_TOTAL ? [] : [{ code: 'OUT_OF_RANGE_SKILL_TOTAL', path, total }]
}
