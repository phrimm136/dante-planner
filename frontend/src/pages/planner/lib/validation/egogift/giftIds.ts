import type { Codes } from '../codes'
import { contentPath, extendPath, field, index } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { firstIndexOf } from '../positions'
import { byPathThenCode } from '../violation'

export const OBSERVATION_GIFT_IDS_PATH = contentPath(field('observationGiftIds'))
export const COMPREHENSIVE_GIFT_IDS_PATH = contentPath(field('comprehensiveGiftIds'))

export type DuplicateGiftCode = Codes<'DUPLICATE_EGOGIFT' | 'DUPLICATE_STARTGIFT'>

export type GiftIdsViolation =
  | { code: DuplicateGiftCode; path: ContentPath }
  | { code: Codes<'UNKNOWN_EGOGIFT'>; path: ContentPath; giftId: string }

export type SelectedGift = { giftId: string; path: ContentPath }

export type GiftReferenceData = { hasEgoGift: (giftId: string) => boolean }

export const selectedGifts = (giftIds: readonly string[], path: ContentPath): SelectedGift[] =>
  giftIds.map((giftId, position) => ({ giftId, path: extendPath(path, index(position)) }))

const unknownGift = (gift: SelectedGift, data: GiftReferenceData): GiftIdsViolation[] =>
  data.hasEgoGift(gift.giftId)
    ? []
    : [{ code: 'UNKNOWN_EGOGIFT', path: gift.path, giftId: gift.giftId }]

export function giftIdViolations(
  giftIds: readonly string[],
  path: ContentPath,
  duplicateCode: DuplicateGiftCode,
  data: GiftReferenceData,
): GiftIdsViolation[] {
  const gifts = selectedGifts(giftIds, path)
  const firstIndex = firstIndexOf(giftIds)
  return gifts
    .flatMap((gift, giftIndex): GiftIdsViolation[] =>
      firstIndex.get(gift.giftId) === giftIndex
        ? unknownGift(gift, data)
        : [{ code: duplicateCode, path: gift.path }],
    )
    .sort(byPathThenCode)
}
