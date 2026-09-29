import { EGOGiftIdSchema, EncodedGiftIdSchema, GIFT_ID_LENGTH } from '@/shared/gameData'
import type { EGOGiftId } from '@/shared/gameData'
import type { ValidationCode } from './codes'
import type { ContentPath } from './contentPath'

export const isBlank = (value: string): boolean => value.trim() === ''

export function decodeGiftId(giftId: string): EGOGiftId | undefined {
  const encoded = EncodedGiftIdSchema.safeParse(giftId)
  return encoded.success ? EGOGiftIdSchema.parse(encoded.data.slice(-GIFT_ID_LENGTH)) : undefined
}

export type Bounds = { min: number; max: number }

export const outOfRangeViolations = <C extends ValidationCode>(
  value: number,
  path: ContentPath,
  bounds: Bounds,
  outOfRange: C,
): { code: C; path: ContentPath }[] =>
  value < bounds.min || value > bounds.max ? [{ code: outOfRange, path }] : []
