import { useState, Suspense, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftListSpec } from '@/hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EGOGiftSpecListSchema } from '@/schemas'
import type { z } from 'zod'
import type { EGOGiftDifficulty, EGOGiftTier, EGOGiftAttributeType } from '@/lib/constants'
import { FilterPageLayout } from '@/components/filter/FilterPageLayout'
import { FilterSection } from '@/components/filter/FilterSection'
import { CompactEGOGiftKeywordFilter } from '@/components/egoGift/CompactEGOGiftKeywordFilter'
import { CompactDifficultyFilter } from '@/components/egoGift/CompactDifficultyFilter'
import { CompactTierFilter } from '@/components/egoGift/CompactTierFilter'
import { ThemePackDropdown } from '@/components/common/ThemePackDropdown'
import { CompactAttributeTypeFilter } from '@/components/filter/CompactAttributeTypeFilter'
import { EGOGiftSearchBar } from '@/components/egoGift/EGOGiftSearchBar'
import { EGOGiftList } from '@/components/egoGift/EGOGiftList'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Card grid section - no longer suspends at grid level.
 * Name search uses deferred hook in EGOGiftList (no suspension).
 */
function EGOGiftCardGrid({
  spec,
  selectedKeywords,
  selectedDifficulties,
  selectedTiers,
  selectedThemePacks,
  selectedAttributeTypes,
  searchQuery,
}: {
  spec: z.infer<typeof EGOGiftSpecListSchema>
  selectedKeywords: Set<string>
  selectedDifficulties: Set<EGOGiftDifficulty>
  selectedTiers: Set<EGOGiftTier>
  selectedThemePacks: Set<string>
  selectedAttributeTypes: Set<EGOGiftAttributeType>
  searchQuery: string
}) {
  // Build EGOGiftListItem array from spec directly (no transformation needed)
  // Name lookup handled by EGOGiftList's deferred hook
  const gifts = useMemo<EGOGiftListItem[]>(
    () =>
      Object.entries(spec).map(([id, specData]) => ({
        id,
        tag: specData.tag as EGOGiftListItem['tag'],
        keyword: specData.keyword,
        attributeType: specData.attributeType,
        themePack: specData.themePack,
        maxEnhancement: specData.maxEnhancement,
        hardOnly: specData.hardOnly,
        extremeOnly: specData.extremeOnly,
      })),
    [spec]
  )

  return (
    <EGOGiftList
      gifts={gifts}
      selectedKeywords={selectedKeywords}
      selectedDifficulties={selectedDifficulties}
      selectedTiers={selectedTiers}
      selectedThemePacks={selectedThemePacks}
      selectedAttributeTypes={selectedAttributeTypes}
      searchQuery={searchQuery}
    />
  )
}

/**
 * Shell component - uses spec data only (no language dependency)
 * Does not suspend on language change since spec query key has no language.
 */
function EGOGiftPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useEGOGiftListSpec()

  // Filter states
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [selectedDifficulties, setSelectedDifficulties] = useState<Set<EGOGiftDifficulty>>(new Set())
  const [selectedTiers, setSelectedTiers] = useState<Set<EGOGiftTier>>(new Set())
  const [selectedThemePacks, setSelectedThemePacks] = useState<Set<string>>(new Set())
  const [selectedAttributeTypes, setSelectedAttributeTypes] = useState<Set<EGOGiftAttributeType>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Reset all filters
  const handleResetAll = () => {
    setSelectedKeywords(new Set())
    setSelectedDifficulties(new Set())
    setSelectedTiers(new Set())
    setSelectedThemePacks(new Set())
    setSelectedAttributeTypes(new Set())
    setSearchQuery('')
  }

  // Cleanup: reset filters on unmount to prevent stale state on navigation back
  useEffect(() => {
    return () => {
      handleResetAll()
    }
  }, [])

  // Calculate active filter count for mobile badge
  const activeFilterCount =
    selectedKeywords.size +
    selectedDifficulties.size +
    selectedTiers.size +
    selectedThemePacks.size +
    selectedAttributeTypes.size

  // Primary filters (always visible on mobile): Keyword and Difficulty
  const primaryFilters = (
    <>
      <FilterSection
        title={t('filters.keyword', 'Keyword')}
        activeCount={selectedKeywords.size}
      >
        <CompactEGOGiftKeywordFilter
          selectedKeywords={selectedKeywords}
          onSelectionChange={setSelectedKeywords}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.difficulty', 'Difficulty')}
        activeCount={selectedDifficulties.size}
      >
        <CompactDifficultyFilter
          selectedDifficulties={selectedDifficulties}
          onSelectionChange={setSelectedDifficulties}
        />
      </FilterSection>
    </>
  )

  // Secondary filters (shown when mobile expanded): Tier, Theme Pack, Attribute Type
  const secondaryFilters = (
    <>
      <FilterSection
        title={t('filters.tier', 'Tier')}
        activeCount={selectedTiers.size}
      >
        <CompactTierFilter
          selectedTiers={selectedTiers}
          onSelectionChange={setSelectedTiers}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.attributeType', 'Attribute')}
        activeCount={selectedAttributeTypes.size}
      >
        <CompactAttributeTypeFilter
          selectedAttributeTypes={selectedAttributeTypes}
          onAttributeTypesChange={setSelectedAttributeTypes}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.themePack', 'Theme Pack')}
        activeCount={selectedThemePacks.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <ThemePackDropdown
            selectedThemePacks={selectedThemePacks}
            onThemePacksChange={setSelectedThemePacks}
          />
        </Suspense>
      </FilterSection>
    </>
  )

  // Combined filter content for desktop sidebar (all filters together)
  const filterContent = (
    <>
      {primaryFilters}
      {secondaryFilters}
    </>
  )

  return (
    <FilterPageLayout
      filterContent={filterContent}
      primaryFilters={primaryFilters}
      secondaryFilters={secondaryFilters}
      activeFilterCount={activeFilterCount}
      onResetAll={handleResetAll}
      searchBar={
        <EGOGiftSearchBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          placeholder={t('pages.egoGift.searchBar', 'Search EGO Gifts...')}
        />
      }
    >
      {/* No Suspense needed - EGOGiftCardGrid doesn't suspend */}
      {/* Spec loading is caught by outer ListPageSkeleton */}
      {/* Name search uses deferred hook in EGOGiftList */}
      <EGOGiftCardGrid
        spec={spec}
        selectedKeywords={selectedKeywords}
        selectedDifficulties={selectedDifficulties}
        selectedTiers={selectedTiers}
        selectedThemePacks={selectedThemePacks}
        selectedAttributeTypes={selectedAttributeTypes}
        searchQuery={searchQuery}
      />
    </FilterPageLayout>
  )
}

/**
 * EGOGiftPage - EGO Gift browser with responsive filter sidebar
 *
 * Granular loading architecture:
 * - Outer Suspense: ListPageSkeleton for spec loading (initial)
 * - Theme pack dropdown: Own Suspense for dropdown i18n
 * - EGOGiftList: Uses deferred hook for name search (no suspension on language change)
 */
export default function EGOGiftPage() {
  return (
    <div className="container mx-auto p-8">
      <Suspense fallback={<ListPageSkeleton preset="egoGift" />}>
        <EGOGiftPageShell />
      </Suspense>
    </div>
  )
}
