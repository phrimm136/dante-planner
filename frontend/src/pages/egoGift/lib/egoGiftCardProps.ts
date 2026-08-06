/**
 * egoGiftCardProps.ts
 *
 * Spec entry to the `gift` prop EGOGiftCard renders.
 */

import type { EGOGiftListItem, EGOGiftSpec } from '../types/EGOGiftTypes'

/** Card props for one gift id, defaulting the list fields a spec entry may omit. */
export function toEGOGiftCardProps(id: string, spec: EGOGiftSpec): EGOGiftListItem {
  return {
    id,
    tag: spec.tag,
    keyword: spec.keyword,
    battleKeywordList: spec.battleKeywordList ?? [],
    attributeType: spec.attributeType,
    themePack: spec.themePack,
    maxEnhancement: spec.maxEnhancement,
  }
}
