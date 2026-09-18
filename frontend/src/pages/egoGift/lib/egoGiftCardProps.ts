import { toEGOGiftEntity } from './egoGiftEntity'

import type { EGOGiftEntity, EGOGiftSpec } from '../types/EGOGiftTypes'

export function toEGOGiftCardProps(id: string, spec: EGOGiftSpec): EGOGiftEntity {
  return toEGOGiftEntity(id, spec)
}
