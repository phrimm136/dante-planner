import { invalidFieldType, parseList } from '../boundary'
import type { InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { contentPath, extendPath, field, index } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { firstIndexOf } from '../positions'
import { byPathThenCode } from '../violation'

export const OBSERVATION_GIFT_IDS_PATH = contentPath(field('observationGiftIds'))
export const COMPREHENSIVE_GIFT_IDS_PATH = contentPath(field('comprehensiveGiftIds'))

export type DuplicateGiftCode = Codes<'DUPLICATE_EGOGIFT' | 'DUPLICATE_STARTGIFT'>

export type GiftIdsViolation =
  | InvalidFieldType
  | { code: DuplicateGiftCode; path: ContentPath }
  | { code: Codes<'UNKNOWN_EGOGIFT'>; path: ContentPath; giftId: string }

export type SelectedGift = { giftId: string; path: ContentPath }

export type ParsedGiftIds = {
  elementCount: number
  gifts: readonly SelectedGift[]
  violations: readonly InvalidFieldType[]
}

export type GiftReferenceData = { hasEgoGift: (giftId: string) => boolean }

type ElementResult = { gifts: SelectedGift[]; violations: InvalidFieldType[] }

function parseElement(value: unknown, path: ContentPath): ElementResult {
  return typeof value === 'string'
    ? { gifts: [{ giftId: value, path }], violations: [] }
    : { gifts: [], violations: [invalidFieldType(path)] }
}

export function parseGiftIds(raw: unknown, path: ContentPath): ParsedGiftIds {
  const list = parseList(raw, path)
  const elements = list.elements.map((value, position) =>
    parseElement(value, extendPath(path, index(position))),
  )
  return {
    elementCount: list.elements.length,
    gifts: elements.flatMap((element) => element.gifts),
    violations: [...list.violations, ...elements.flatMap((element) => element.violations)],
  }
}

const unknownGift = (gift: SelectedGift, data: GiftReferenceData): GiftIdsViolation[] =>
  data.hasEgoGift(gift.giftId)
    ? []
    : [{ code: 'UNKNOWN_EGOGIFT', path: gift.path, giftId: gift.giftId }]

export function giftIdViolations(
  parsed: ParsedGiftIds,
  duplicateCode: DuplicateGiftCode,
  data: GiftReferenceData,
): GiftIdsViolation[] {
  const firstIndex = firstIndexOf(parsed.gifts.map((gift) => gift.giftId))
  const ruleViolations = parsed.gifts.flatMap((gift, giftIndex): GiftIdsViolation[] =>
    firstIndex.get(gift.giftId) === giftIndex
      ? unknownGift(gift, data)
      : [{ code: duplicateCode, path: gift.path }],
  )
  return [...parsed.violations, ...ruleViolations].sort(byPathThenCode)
}
