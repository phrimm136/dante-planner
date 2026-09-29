import { decodeGiftId, invalidFieldType, isAbsent, isBlank } from '../boundary'
import type { InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { contentPath, field } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { firstIndexOf } from '../positions'
import { byPathThenCode } from '../violation'

export const START_GIFT_IDS_PATH = contentPath(field('selectedGiftIds'))

const START_GIFT_KEYWORD_PATH = contentPath(field('selectedGiftKeyword'))

export type StartGiftViolation =
  | InvalidFieldType
  | {
      code: Codes<
        'MISSING_STARTGIFT_KEYWORD' | 'UNKNOWN_STARTGIFT_KEYWORD' | 'MISMATCHED_STARTGIFT'
      >
      path: ContentPath
    }

export type StartGift = { giftId: string; path: ContentPath }

export type StartGifts = { elementCount: number; gifts: readonly StartGift[] }

export type StartGiftReferenceData = { hasEgoGift: (giftId: string) => boolean }

export type StartGiftSeason = {
  hasStartGiftKeyword: (keyword: string) => boolean
  inStartGiftPool: (keyword: string, baseGiftId: string) => boolean
}

type ParsedKeyword =
  | { kind: 'chosen'; keyword: string }
  | { kind: 'absent' }
  | { kind: 'rejected'; violation: InvalidFieldType }

function parseKeyword(raw: unknown): ParsedKeyword {
  if (typeof raw === 'string')
    return isBlank(raw) ? { kind: 'absent' } : { kind: 'chosen', keyword: raw }
  return isAbsent(raw)
    ? { kind: 'absent' }
    : { kind: 'rejected', violation: invalidFieldType(START_GIFT_KEYWORD_PATH) }
}

const baseGiftId = (giftId: string): string => decodeGiftId(giftId) ?? giftId

function outsidePool(
  keyword: string,
  { gifts }: StartGifts,
  data: StartGiftReferenceData,
  season: StartGiftSeason,
): StartGiftViolation[] {
  const firstIndex = firstIndexOf(gifts.map((gift) => gift.giftId))
  return gifts
    .filter((gift, giftIndex) => firstIndex.get(gift.giftId) === giftIndex)
    .filter((gift) => data.hasEgoGift(gift.giftId))
    .filter((gift) => !season.inStartGiftPool(keyword, baseGiftId(gift.giftId)))
    .map((gift) => ({ code: 'MISMATCHED_STARTGIFT', path: gift.path }))
}

const missingKeyword = ({ elementCount }: StartGifts): StartGiftViolation[] =>
  elementCount > 0 ? [{ code: 'MISSING_STARTGIFT_KEYWORD', path: START_GIFT_IDS_PATH }] : []

function chosenKeywordViolations(
  keyword: string,
  startGifts: StartGifts,
  data: StartGiftReferenceData,
  season: StartGiftSeason,
): StartGiftViolation[] {
  return season.hasStartGiftKeyword(keyword)
    ? outsidePool(keyword, startGifts, data, season)
    : [{ code: 'UNKNOWN_STARTGIFT_KEYWORD', path: START_GIFT_KEYWORD_PATH }]
}

function seasonViolations(
  keyword: ParsedKeyword,
  startGifts: StartGifts,
  data: StartGiftReferenceData,
  season: StartGiftSeason,
): StartGiftViolation[] {
  switch (keyword.kind) {
    case 'rejected':
      return []
    case 'absent':
      return missingKeyword(startGifts)
    case 'chosen':
      return chosenKeywordViolations(keyword.keyword, startGifts, data, season)
  }
}

export function startGiftViolations(
  rawKeyword: unknown,
  startGifts: StartGifts,
  data: StartGiftReferenceData,
  season: StartGiftSeason | undefined,
): StartGiftViolation[] {
  const keyword = parseKeyword(rawKeyword)
  return [
    ...(keyword.kind === 'rejected' ? [keyword.violation] : []),
    ...(season === undefined ? [] : seasonViolations(keyword, startGifts, data, season)),
  ].sort(byPathThenCode)
}
