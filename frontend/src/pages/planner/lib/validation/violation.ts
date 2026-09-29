import { compareContentPaths } from './contentPath'
import type { ContentPath } from './contentPath'

export type FloorRuleCode =
  | 'INVALID_FIELD_TYPE'
  | 'FLOOR_MISSING_THEME_PACK'
  | 'FLOOR_DUPLICATE_THEME_PACK'
  | 'INVALID_SEQUENCE'
  | 'VALUE_OUT_OF_RANGE'
  | 'DUPLICATE_VALUE'

export type FloorViolationCode =
  | FloorRuleCode
  | 'THEME_PACK_UNKNOWN_ID'
  | 'FLOOR_UNKNOWN_GIFT_ID'
  | 'GIFT_NOT_AFFORDABLE'

export type Violation = { code: FloorViolationCode; path: ContentPath }

export const byPathThenCode = (a: Violation, b: Violation): number => {
  const byPath = compareContentPaths(a.path, b.path)
  if (byPath !== 0) return byPath
  if (a.code !== b.code) return a.code < b.code ? -1 : 1
  return 0
}
