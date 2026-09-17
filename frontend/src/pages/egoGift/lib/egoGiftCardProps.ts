/**
 * egoGiftCardProps.ts
 *
 * Spec entry to the `gift` prop EGOGiftCard renders.
 */

import { toEGOGiftEntity } from './egoGiftEntity'

import type { EGOGiftEntity, EGOGiftSpec } from '../types/EGOGiftTypes'

/** Card props for one gift id. */
export function toEGOGiftCardProps(id: string, spec: EGOGiftSpec): EGOGiftEntity {
  return toEGOGiftEntity(id, spec)
}
