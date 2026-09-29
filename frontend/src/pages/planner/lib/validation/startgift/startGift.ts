import { invalidFieldType, isAbsent } from '../boundary'
import type { InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { contentPath, field } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { byPathThenCode } from '../violation'

export const START_GIFT_IDS_PATH = contentPath(field('selectedGiftIds'))

const START_GIFT_KEYWORD_PATH = contentPath(field('selectedGiftKeyword'))

export type StartGiftViolation =
  | InvalidFieldType
  | { code: Codes<'MISSING_STARTGIFT_KEYWORD'>; path: ContentPath }

type ParsedKeyword = { keyword?: string; violations: InvalidFieldType[] }

function parseKeyword(raw: unknown): ParsedKeyword {
  if (typeof raw === 'string')
    return raw === '' ? { violations: [] } : { keyword: raw, violations: [] }
  return isAbsent(raw)
    ? { violations: [] }
    : { violations: [invalidFieldType(START_GIFT_KEYWORD_PATH)] }
}

export function startGiftViolations(
  rawKeyword: unknown,
  startGiftCount: number,
): StartGiftViolation[] {
  const { keyword, violations } = parseKeyword(rawKeyword)
  const keywordMissing: StartGiftViolation[] =
    keyword === undefined && startGiftCount > 0
      ? [{ code: 'MISSING_STARTGIFT_KEYWORD', path: START_GIFT_IDS_PATH }]
      : []
  return [...violations, ...keywordMissing].sort(byPathThenCode)
}
