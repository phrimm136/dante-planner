import { SinnerScopedIdSchema } from '@/shared/gameData'

const SINNER_DIGITS_START = 1
const SINNER_DIGITS_END = 3
const SINNER_DIGITS = SINNER_DIGITS_END - SINNER_DIGITS_START

export function belongsToSinner(entityId: string, sinnerKey: string): boolean {
  const parsed = SinnerScopedIdSchema.safeParse(entityId)
  return (
    parsed.success &&
    parsed.data.slice(SINNER_DIGITS_START, SINNER_DIGITS_END) ===
      sinnerKey.padStart(SINNER_DIGITS, '0')
  )
}
