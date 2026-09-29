import { decodeGiftId } from '../boundary'
import type { Codes } from '../codes'
import { contentPath, extendPath, field, index } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { firstIndexOf } from '../positions'
import { byPathThenCode } from '../violation'

export const START_GIFT_IDS_PATH = contentPath(field('selectedGiftIds'))

const START_GIFT_KEYWORD_PATH = contentPath(field('selectedGiftKeyword'))

export type StartGiftViolation = {
  code: Codes<'MISSING_STARTGIFT_KEYWORD' | 'UNKNOWN_STARTGIFT_KEYWORD' | 'MISMATCHED_STARTGIFT'>
  path: ContentPath
}

export type StartGiftReferenceData = { hasEgoGift: (giftId: string) => boolean }

export type StartGiftSeason = {
  hasStartGiftKeyword: (keyword: string) => boolean
  inStartGiftPool: (keyword: string, baseGiftId: string) => boolean
}

const baseGiftId = (giftId: string): string => decodeGiftId(giftId) ?? giftId

function outsidePool(
  keyword: string,
  giftIds: readonly string[],
  data: StartGiftReferenceData,
  season: StartGiftSeason,
): StartGiftViolation[] {
  const firstIndex = firstIndexOf(giftIds)
  return giftIds
    .map((giftId, position) => ({ giftId, position }))
    .filter(({ giftId, position }) => firstIndex.get(giftId) === position)
    .filter(({ giftId }) => data.hasEgoGift(giftId))
    .filter(({ giftId }) => !season.inStartGiftPool(keyword, baseGiftId(giftId)))
    .map(({ position }) => ({
      code: 'MISMATCHED_STARTGIFT',
      path: extendPath(START_GIFT_IDS_PATH, index(position)),
    }))
}

const missingKeyword = (giftIds: readonly string[]): StartGiftViolation[] =>
  giftIds.length > 0 ? [{ code: 'MISSING_STARTGIFT_KEYWORD', path: START_GIFT_IDS_PATH }] : []

export function startGiftViolations(
  keyword: string | null,
  giftIds: readonly string[],
  data: StartGiftReferenceData,
  season: StartGiftSeason,
): StartGiftViolation[] {
  if (keyword === null) return missingKeyword(giftIds)
  const violations: StartGiftViolation[] = season.hasStartGiftKeyword(keyword)
    ? outsidePool(keyword, giftIds, data, season)
    : [{ code: 'UNKNOWN_STARTGIFT_KEYWORD', path: START_GIFT_KEYWORD_PATH }]
  return violations.sort(byPathThenCode)
}
