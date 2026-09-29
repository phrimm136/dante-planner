import { OFFENSIVE_SKILL_SLOTS } from '@/shared/gameData'
import type { Codes } from '../codes'
import { extendPath, key } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { byPathThenCode } from '../violation'

const SKILL_SLOTS: ReadonlySet<string> = new Set(OFFENSIVE_SKILL_SLOTS.map(String))

const SKILL_EA_TOTAL = 6

export type SkillViolation =
  | { code: Codes<'INVALID_SKILL_SLOT'>; path: ContentPath }
  | { code: Codes<'OUT_OF_RANGE_SKILL_TOTAL'>; path: ContentPath; total: number }

const isFiniteNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value)

export function sinnerSkillViolations(
  slots: Record<string, unknown>,
  path: ContentPath,
): SkillViolation[] {
  const slotKeys = Object.keys(slots)
  const invalidSlots: SkillViolation[] = slotKeys
    .filter((slotKey) => !SKILL_SLOTS.has(slotKey))
    .map((slotKey) => ({ code: 'INVALID_SKILL_SLOT', path: extendPath(path, key(slotKey)) }))
  const total = slotKeys
    .filter((slotKey) => SKILL_SLOTS.has(slotKey))
    .map((slotKey) => slots[slotKey])
    .filter(isFiniteNumber)
    .reduce((sum, points) => sum + points, 0)
  const totalViolations: SkillViolation[] =
    total === SKILL_EA_TOTAL ? [] : [{ code: 'OUT_OF_RANGE_SKILL_TOTAL', path, total }]
  return [...invalidSlots, ...totalViolations].sort(byPathThenCode)
}
