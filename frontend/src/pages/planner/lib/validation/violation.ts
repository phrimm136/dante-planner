import { compareContentPaths } from './contentPath'
import type { ContentPath } from './contentPath'
import type { Codes, ValidationCode } from './codes'

export const DIFFICULTY_RANGE_CODES = [
  'OUT_OF_RANGE_DIFFICULTY',
  'INSUFFICIENT_DIFFICULTY_ENTRY',
  'OUT_OF_RANGE_DIFFICULTY_NOT_HARD',
  'OUT_OF_RANGE_DIFFICULTY_NOT_EXTREME',
] as const

export type DifficultyRangeCode = Codes<(typeof DIFFICULTY_RANGE_CODES)[number]>

export const isDifficultyRangeCode = (code: string): code is DifficultyRangeCode =>
  (DIFFICULTY_RANGE_CODES as readonly string[]).includes(code)

export type ShapeCode = Codes<'MISSING_REQUIRED_FIELD' | 'UNKNOWN_FIELD' | 'INVALID_FIELD_TYPE'>

export type FloorRuleCode =
  | Codes<
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
  | ShapeCode
  | Codes<'UNKNOWN_THEMEPACK' | 'UNKNOWN_EGOGIFT_FLOOR' | 'UNAFFORDABLE_EGOGIFT'>

export type Violation = { code: FloorViolationCode; path: ContentPath }

export type Located = { code: ValidationCode; path: ContentPath }

export const byPathThenCode = (a: Located, b: Located): number => {
  const byPath = compareContentPaths(a.path, b.path)
  if (byPath !== 0) return byPath
  if (a.code !== b.code) return a.code < b.code ? -1 : 1
  return 0
}
