/**
 * Deck Builder Filter Predicate
 *
 * Pure function that evaluates whether an identity or EGO matches the
 * current deck filter state. Mode-gated: identity-only fields (def type,
 * rank, unit keywords) are ignored when mode is 'ego'; ego-only field
 * (ego type) is ignored when mode is 'identity'.
 */

import type { DeckFilterState, EntityMode } from '@/types/DeckTypes'
import type { IdentityListItem } from '@/types/IdentityTypes'
import type { EGOListItem } from '@/types/EGOTypes'
import type { SearchMappings } from '@/hooks/useSearchMappings'
import type { Keyword } from '@/lib/constants'
import { getSinnerFromId } from '@/lib/utils'

type DeckFilterItem = IdentityListItem | EGOListItem

/**
 * Evaluates whether an item passes all active deck filters for the given mode.
 *
 * Semantics:
 * - Empty filter sets match everything (short-circuit when size === 0).
 * - Sinner: derived from entity ID; item's sinner must be in the selected set.
 * - Skill keywords: item must have ALL selected keywords (AND).
 * - Attribute / Atk / Def / Rank / Season / Unit / Battle keywords: ANY match (OR).
 * - EGO type: item.egoType in set (EGO mode only).
 * - Mode gating: identity-only fields skipped when mode is 'ego' and vice versa.
 * - Search: lowercased match against item.name, skill-keyword display names, and
 *   (identity mode only) unit-keyword display names.
 *
 * @param item - IdentityListItem or EGOListItem to evaluate
 * @param state - Current deck filter state (from Zustand slice)
 * @param mode - Entity mode gate; controls which id/ego-specific fields apply
 * @param searchMappings - Reverse mappings from display name to internal codes
 * @returns true if item passes every active filter for this mode
 */
export function matchesDeckFilter(
  item: DeckFilterItem,
  state: DeckFilterState,
  mode: EntityMode,
  searchMappings: SearchMappings
): boolean {
  if (state.selectedSinners.size > 0) {
    if (!state.selectedSinners.has(getSinnerFromId(item.id))) return false
  }

  if (state.selectedKeywords.size > 0) {
    const keywords = Array.from(state.selectedKeywords)
    const hasAllKeywords = keywords.every((kw) => item.skillKeywordList.includes(kw as Keyword))
    if (!hasAllKeywords) return false
  }

  if (state.selectedAttributes.size > 0) {
    const hasAny = item.attributeTypes.some((attr) => state.selectedAttributes.has(attr))
    if (!hasAny) return false
  }

  if (state.selectedAtkTypes.size > 0) {
    const hasAny = item.atkTypes.some((atk) => state.selectedAtkTypes.has(atk))
    if (!hasAny) return false
  }

  if (state.selectedSeasons.size > 0) {
    if (!state.selectedSeasons.has(item.season)) return false
  }

  if (state.selectedBattleKeywords.size > 0) {
    const hasAny = (item.battleKeywordList ?? []).some((kw) => state.selectedBattleKeywords.has(kw))
    if (!hasAny) return false
  }

  if (mode === 'identity') {
    const identity = item as IdentityListItem

    if (state.selectedDefTypes.size > 0) {
      const hasAny = identity.defenseTypes.some((def) => state.selectedDefTypes.has(def))
      if (!hasAny) return false
    }

    if (state.selectedRaritys.size > 0) {
      if (!state.selectedRaritys.has(identity.rank)) return false
    }

    if (state.selectedUnitKeywords.size > 0) {
      const hasAny = identity.unitKeywordList.some((kw) => state.selectedUnitKeywords.has(kw))
      if (!hasAny) return false
    }
  } else {
    const ego = item as EGOListItem

    if (state.selectedEgoTypes.size > 0) {
      if (!state.selectedEgoTypes.has(ego.egoType)) return false
    }
  }

  if (state.searchQuery) {
    const lowerQuery = state.searchQuery.toLowerCase()
    const nameMatch = item.name?.toLowerCase().includes(lowerQuery) ?? false

    const keywordMatch = Array.from(searchMappings.keywordToValue.entries()).some(
      ([naturalLang, internalCodes]) => {
        if (!naturalLang.includes(lowerQuery)) return false
        return internalCodes.some((code) => item.skillKeywordList.includes(code as Keyword))
      }
    )

    let unitKeywordMatch = false
    if (mode === 'identity') {
      const identity = item as IdentityListItem
      unitKeywordMatch = Array.from(searchMappings.unitKeywordToValue.entries()).some(
        ([naturalLang, internalCodes]) => {
          if (!naturalLang.includes(lowerQuery)) return false
          return internalCodes.some((code) => identity.unitKeywordList.includes(code))
        }
      )
    }

    if (!nameMatch && !keywordMatch && !unitKeywordMatch) return false
  }

  return true
}
