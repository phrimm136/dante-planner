import { OFFENSIVE_SKILL_SLOTS } from '@/shared/gameData'
import { invalidFieldType, isInteger } from '../boundary'
import type { InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { extendPath, key } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { byPathThenCode } from '../violation'

const SKILL_SLOTS: ReadonlySet<string> = new Set(OFFENSIVE_SKILL_SLOTS.map(String))

const SKILL_EA_TOTAL = 6

export type SkillViolation =
  | InvalidFieldType
  | { code: Codes<'INVALID_SKILL_SLOT'>; path: ContentPath }
  | { code: Codes<'OUT_OF_RANGE_SKILL_TOTAL'>; path: ContentPath; total: number }

const totalViolations = (points: readonly number[], path: ContentPath): SkillViolation[] => {
  const total = points.reduce((sum, slotPoints) => sum + slotPoints, 0)
  return total === SKILL_EA_TOTAL ? [] : [{ code: 'OUT_OF_RANGE_SKILL_TOTAL', path, total }]
}

export function sinnerSkillViolations(
  slots: Record<string, unknown>,
  path: ContentPath,
): SkillViolation[] {
  const slotKeys = Object.keys(slots)
  const invalidSlots: SkillViolation[] = slotKeys
    .filter((slotKey) => !SKILL_SLOTS.has(slotKey))
    .map((slotKey) => ({ code: 'INVALID_SKILL_SLOT', path: extendPath(path, key(slotKey)) }))
  const values = slotKeys
    .filter((slotKey) => SKILL_SLOTS.has(slotKey))
    .map((slotKey) => ({ value: slots[slotKey], path: extendPath(path, key(slotKey)) }))
  const malformed = values.filter(({ value }) => !isInteger(value)).map((slot) => slot.path)
  const points = values.flatMap(({ value }) => (isInteger(value) ? [value] : []))
  return [
    ...invalidSlots,
    ...malformed.map(invalidFieldType),
    ...(malformed.length === 0 ? totalViolations(points, path) : []),
  ].sort(byPathThenCode)
}
