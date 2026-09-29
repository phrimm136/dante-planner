import { compareContentPaths } from './contentPath'
import type { ContentPath } from './contentPath'
import type { Codes } from './codes'

export const DIFFICULTY_RANGE_CODES = [
  'OUT_OF_RANGE_DIFFICULTY',
  'INSUFFICIENT_DIFFICULTY_ENTRY',
  'OUT_OF_RANGE_DIFFICULTY_NOT_HARD',
  'OUT_OF_RANGE_DIFFICULTY_NOT_EXTREME',
] as const

export type DifficultyRangeCode = Codes<(typeof DIFFICULTY_RANGE_CODES)[number]>

export const isDifficultyRangeCode = (code: string): code is DifficultyRangeCode =>
  (DIFFICULTY_RANGE_CODES as readonly string[]).includes(code)

export type FloorRuleCode =
  | Codes<
      | 'INVALID_FIELD_TYPE'
      | 'INSUFFICIENT_FLOORS'
      | 'MISSING_THEMEPACK'
      | 'DUPLICATE_THEMEPACK'
      | 'OUT_OF_ORDER_THEMEPACK'
      | 'OUT_OF_ORDER_DIFFICULTY'
      | 'DUPLICATE_EGOGIFT'
    >
  | DifficultyRangeCode

export type FloorViolationCode =
  | FloorRuleCode
  | Codes<'UNKNOWN_THEMEPACK' | 'UNKNOWN_EGOGIFT_FLOOR' | 'UNAFFORDABLE_EGOGIFT'>

export type Violation = { code: FloorViolationCode; path: ContentPath }

export const byPathThenCode = (a: Violation, b: Violation): number => {
  const byPath = compareContentPaths(a.path, b.path)
  if (byPath !== 0) return byPath
  if (a.code !== b.code) return a.code < b.code ? -1 : 1
  return 0
}
