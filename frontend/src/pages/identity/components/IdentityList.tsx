import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import type { IdentityListItem } from '../types/IdentityTypes'
import { useSearchMappingsDeferred } from '@/shared/filter'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { useIdentityListI18nDeferred } from '../hooks/useIdentityListData'
import { type Season, type SkillAttributeType, type AtkType, type DefType } from '@/shared/gameData'
import { CARD_GRID, PROGRESSIVE_REVEAL } from '@/lib/constants'
import { sortByReleaseDate } from '@/shared/filter'
import { getSinnerFromId } from '@/shared/gameData'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import { IdentityCardLink } from './IdentityCardLink'

interface IdentityListProps {
  identities: IdentityListItem[]
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  selectedBattleKeywords: Set<string>
  selectedAttributes: Set<SkillAttributeType>
  selectedAtkTypes: Set<AtkType>
  selectedDefTypes: Set<DefType>
  selectedRaritys: Set<number>
  selectedSeasons: Set<Season>
  selectedUnitKeywords: Set<string>
  searchQuery: string
}

/**
 * IdentityList - Renders list of identity cards with CSS-based filtering
 *
 * All cards are rendered once, visibility is toggled via CSS class.
 * This eliminates React reconciliation on filter changes.
 *
 * Filter Logic:
 * - All filter types use AND between each other
 * - Sinner: OR logic (any selected sinner)
 * - Keyword: AND logic (must have ALL selected keywords)
 * - Attribute: AND logic (must have ALL selected attributes)
 * - Attack Type: AND logic (must have ALL selected attack types)
 * - Rank: OR logic (any selected rank)
 * - Season: OR logic (any selected season)
 * - Association: OR logic (any selected association)
 * - Search: OR logic (name OR keyword OR trait)
 */
export function IdentityList({
  identities,
  selectedSinners,
  selectedKeywords,
  selectedBattleKeywords,
  selectedAttributes,
  selectedAtkTypes,
  selectedDefTypes,
  selectedRaritys,
  selectedSeasons,
  selectedUnitKeywords,
  searchQuery,
}: IdentityListProps) {
  const { t } = useTranslation('database')
  // Non-suspending: returns empty mappings while loading, search won't match until loaded
  const { keywordToValue, unitKeywordToValue } = useSearchMappingsDeferred()
  // Non-suspending: returns empty object while loading, name search won't match until loaded
  const identityNames = useIdentityListI18nDeferred()

  // Sort all identities once (stable order for CSS-based filtering)
  const sortedIdentities = useMemo(
    () => sortByReleaseDate(identities),
    [identities]
  )

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: sortedIdentities.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
    resetKey: sortedIdentities,
  })

  // Create Set of visible identity IDs based on filters
  // This is fast O(n) computation, much cheaper than React reconciliation
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()

    // Cache array conversions before loop to avoid O(N×M) allocations
    const keywordEntries = Array.from(keywordToValue.entries())
    const unitKeywordEntries = Array.from(unitKeywordToValue.entries())

    for (const identity of sortedIdentities) {
      // Sinner filter - OR logic (match any selected sinner)
      if (selectedSinners.size > 0) {
        if (!selectedSinners.has(getSinnerFromId(identity.id))) continue
      }

      // Keyword filter - AND logic (identity must have ALL selected keywords)
      if (selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
          identity.skillKeywordList.includes(selectedKeyword)
        )
        if (!hasAllKeywords) continue
      }

      // Battle keyword filter - OR logic (identity must have ANY selected battle keyword)
      if (selectedBattleKeywords.size > 0) {
        const hasAnyBattleKeyword = (identity.battleKeywordList ?? []).some((keyword) =>
          selectedBattleKeywords.has(keyword)
        )
        if (!hasAnyBattleKeyword) continue
      }

      // Attribute filter - AND logic (identity must have ALL selected attributes)
      if (selectedAttributes.size > 0) {
        const hasAllAttributes = Array.from(selectedAttributes).every((attr) =>
          identity.attributeTypes.includes(attr)
        )
        if (!hasAllAttributes) continue
      }

      // Attack type filter - AND logic (identity must have ALL selected attack types)
      if (selectedAtkTypes.size > 0) {
        const hasAllAtkTypes = Array.from(selectedAtkTypes).every((atkType) =>
          identity.atkTypes.includes(atkType)
        )
        if (!hasAllAtkTypes) continue
      }

      // Defense type filter - AND logic (identity must have ALL selected defense types)
      if (selectedDefTypes.size > 0) {
        const hasAllDefTypes = Array.from(selectedDefTypes).every((defType) =>
          identity.defenseTypes.includes(defType)
        )
        if (!hasAllDefTypes) continue
      }

      // Rarity filter - OR logic (identity rarity matches ANY selected rarity)
      if (selectedRaritys.size > 0) {
        if (!selectedRaritys.has(identity.rank)) continue
      }

      // Season filter - OR logic (identity season matches ANY selected season)
      if (selectedSeasons.size > 0) {
        if (!selectedSeasons.has(identity.season)) continue
      }

      // Unit keyword filter - OR logic (identity has ANY selected unit keyword in unitKeywordList)
      if (selectedUnitKeywords.size > 0) {
        const hasAnyUnitKeyword = identity.unitKeywordList.some((keyword) =>
          selectedUnitKeywords.has(keyword)
        )
        if (!hasAnyUnitKeyword) continue
      }

      // Search filter - match name OR keyword OR trait (both deferred, no suspension)
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()

        // Check name match (partial, case-insensitive)
        const identityName = identityNames[identity.id] ?? ''
        const nameMatch = identityName.toLowerCase().includes(lowerQuery)

        // Check keyword match (partial match on natural language, then lookup bracketed values)
        const keywordMatch = keywordEntries.some(([naturalLang, bracketedValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return bracketedValues.some((bracketedValue) => identity.skillKeywordList.includes(bracketedValue))
          }
          return false
        })

        // Check unit keyword match (partial match on natural language, then lookup internal codes)
        const unitKeywordMatch = unitKeywordEntries.some(([naturalLang, internalCodes]) => {
          if (naturalLang.includes(lowerQuery)) {
            return internalCodes.some((internalCode) => identity.unitKeywordList.includes(internalCode))
          }
          return false
        })

        // Must match at least one category
        if (!nameMatch && !keywordMatch && !unitKeywordMatch) continue
      }

      ids.add(identity.id)
    }

    return ids
  }, [
    sortedIdentities,
    selectedSinners,
    selectedKeywords,
    selectedBattleKeywords,
    selectedAttributes,
    selectedAtkTypes,
    selectedDefTypes,
    selectedRaritys,
    selectedSeasons,
    selectedUnitKeywords,
    searchQuery,
    keywordToValue,
    unitKeywordToValue,
    identityNames,
  ])

  if (visibleIds.size === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-muted-foreground py-8">
          {t('identity.emptyState')}
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      {/* Responsive grid layout with padding for sinner icons/bg */}
      <div className="pt-4">
        <ResponsiveCardGrid
          cardWidth={CARD_GRID.WIDTH.IDENTITY}
          cardHeight={CARD_GRID.HEIGHT.IDENTITY}
          mobileScale={0.8}
        >
          {sortedIdentities.slice(0, displayCount).map((identity) => (
            <ScaledCardWrapper
              key={identity.id}
              mobileScale={0.8}
              cardWidth={CARD_GRID.WIDTH.IDENTITY}
              cardHeight={CARD_GRID.HEIGHT.IDENTITY}
              className={visibleIds.has(identity.id) ? '' : 'hidden'}
            >
              <IdentityCardLink identity={identity} />
            </ScaledCardWrapper>
          ))}
        </ResponsiveCardGrid>
      </div>
    </div>
  )
}
