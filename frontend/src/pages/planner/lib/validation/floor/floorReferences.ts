import type { ContentPath } from '../contentPath'
import { byPathThenCode } from '../violation'
import { acceptedFloors, giftIdPath, themePackPath } from './floorRules'
import type { FloorSelectionValue, ParsedFloors } from './floorRules'
import { firstIndexOf } from './positions'

export type FloorReferenceData = {
  hasThemePack: (themePackId: string) => boolean
  hasEgoGift: (giftId: string) => boolean
  isGiftAffordableForThemePack: (giftId: string, themePackId: string) => boolean
}

export type FloorReferenceViolation =
  | { code: 'THEME_PACK_UNKNOWN_ID'; path: ContentPath; themePackId: string }
  | { code: 'FLOOR_UNKNOWN_GIFT_ID'; path: ContentPath; giftId: string }
  | { code: 'GIFT_NOT_AFFORDABLE'; path: ContentPath; giftId: string; themePackId: string }

const themePackViolations = (
  floor: FloorSelectionValue,
  floorIndex: number,
  data: FloorReferenceData,
): FloorReferenceViolation[] =>
  floor.themePackId === undefined || data.hasThemePack(floor.themePackId)
    ? []
    : [
        {
          code: 'THEME_PACK_UNKNOWN_ID',
          path: themePackPath(floorIndex),
          themePackId: floor.themePackId,
        },
      ]

function giftViolation(
  giftId: string,
  path: ContentPath,
  themePackId: string | undefined,
  data: FloorReferenceData,
): FloorReferenceViolation[] {
  if (!data.hasEgoGift(giftId)) return [{ code: 'FLOOR_UNKNOWN_GIFT_ID', path, giftId }]
  if (themePackId === undefined || data.isGiftAffordableForThemePack(giftId, themePackId)) return []
  return [{ code: 'GIFT_NOT_AFFORDABLE', path, giftId, themePackId }]
}

const giftViolations = (
  floor: FloorSelectionValue,
  floorIndex: number,
  data: FloorReferenceData,
): FloorReferenceViolation[] => {
  const firstIndex = firstIndexOf(floor.giftIds)
  return floor.giftIds.flatMap((giftId, giftIndex) =>
    firstIndex.get(giftId) === giftIndex
      ? giftViolation(giftId, giftIdPath(floorIndex, giftIndex), floor.themePackId, data)
      : [],
  )
}

export function floorReferenceViolations(
  parsed: ParsedFloors,
  data: FloorReferenceData,
): FloorReferenceViolation[] {
  if (parsed.violations.length > 0) return []
  return acceptedFloors(parsed)
    .flatMap((floor, floorIndex) =>
      themePackViolations(floor, floorIndex, data).concat(giftViolations(floor, floorIndex, data)),
    )
    .sort(byPathThenCode)
}
