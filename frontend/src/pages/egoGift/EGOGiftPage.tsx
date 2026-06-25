import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftListSpec } from '@/pages/egoGift'
import type { EGOGiftListItem, EGOGiftSpecListSchema } from '@/pages/egoGift'
import type { z } from 'zod'
import type { EGOGiftDifficulty, EGOGiftTier, EGOGiftAttributeType } from '@/lib/constants'
import { BOOLEAN_FILTER_OPTIONS } from '@/lib/constants'
import { FilterPageLayout } from '@/components/filter/FilterPageLayout'
import { FilterSection } from '@/components/filter/FilterSection'
import { CompactEGOGiftKeywordFilter } from '@/pages/egoGift'
import { CompactDifficultyFilter } from '@/pages/egoGift'
import { CompactTierFilter } from '@/pages/egoGift'
import { ThemePackDropdown } from '@/components/filter/ThemePackDropdown'
import { BattleKeywordDropdown } from '@/components/filter/BattleKeywordDropdown'
import { CompactAttributeTypeFilter } from '@/components/filter/CompactAttributeTypeFilter'
import { CompactIconFilter } from '@/components/filter/CompactIconFilter'
import { SearchBar } from '@/components/common/SearchBar'
import { EGOGiftList } from '@/pages/egoGift'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Card grid section - no longer suspends at grid level.
 * Name search uses deferred hook in EGOGiftList (no suspension).
 */
function EGOGiftCardGrid({
  spec,
  selectedKeywords,
  selectedBattleKeywords,
  selectedDifficulties,
  selectedTiers,
  selectedThemePacks,
  selectedAttributeTypes,
  selectedFusioned,
  selectedExclusive,
  searchQuery,
}: {
  spec: z.infer<typeof EGOGiftSpecListSchema>
  selectedKeywords: Set<string>
  selectedBattleKeywords: Set<string>
  selectedDifficulties: Set<EGOGiftDifficulty>
  selectedTiers: Set<EGOGiftTier>
  selectedThemePacks: Set<string>
  selectedAttributeTypes: Set<EGOGiftAttributeType>
  selectedFusioned: Set<string>
  selectedExclusive: Set<string>
  searchQuery: string
}) {
  // Build EGOGiftListItem array from spec directly
  // Name lookup handled by EGOGiftList's deferred hook
  const gifts = useMemo<EGOGiftListItem[]>(
    () =>
      Object.entries(spec).map(([id, specData]) => ({
        id,
        tag: specData.tag as EGOGiftListItem['tag'],
        keyword: specData.keyword,
        battleKeywordList: specData.battleKeywordList,
        attributeType: specData.attributeType,
        themePack: specData.themePack,
        maxEnhancement: specData.maxEnhancement,
        hardOnly: specData.hardOnly,
        extremeOnly: specData.extremeOnly,
        fusioned: specData.fusioned,
      })),
    [spec]
  )

  return (
    <EGOGiftList
      gifts={gifts}
      selectedKeywords={selectedKeywords}
      selectedBattleKeywords={selectedBattleKeywords}
      selectedDifficulties={selectedDifficulties}
      selectedTiers={selectedTiers}
      selectedThemePacks={selectedThemePacks}
      selectedAttributeTypes={selectedAttributeTypes}
      selectedFusioned={selectedFusioned}
      selectedExclusive={selectedExclusive}
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
  const [selectedBattleKeywords, setSelectedBattleKeywords] = useState<Set<string>>(new Set())
  const [selectedDifficulties, setSelectedDifficulties] = useState<Set<EGOGiftDifficulty>>(new Set())
  const [selectedTiers, setSelectedTiers] = useState<Set<EGOGiftTier>>(new Set())
  const [selectedThemePacks, setSelectedThemePacks] = useState<Set<string>>(new Set())
  const [selectedAttributeTypes, setSelectedAttributeTypes] = useState<Set<EGOGiftAttributeType>>(new Set())
  const [selectedFusioned, setSelectedFusioned] = useState<Set<string>>(new Set())
  const [selectedExclusive, setSelectedExclusive] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Reset all filters
  const handleResetAll = () => {
    setSelectedKeywords(new Set())
    setSelectedBattleKeywords(new Set())
    setSelectedDifficulties(new Set())
    setSelectedTiers(new Set())
    setSelectedThemePacks(new Set())
    setSelectedAttributeTypes(new Set())
    setSelectedFusioned(new Set())
    setSelectedExclusive(new Set())
    setSearchQuery('')
  }


  // Calculate active filter count for mobile badge
  const activeFilterCount =
    selectedKeywords.size +
    selectedBattleKeywords.size +
    selectedDifficulties.size +
    selectedTiers.size +
    selectedThemePacks.size +
    selectedAttributeTypes.size +
    selectedFusioned.size +
    selectedExclusive.size

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
        title={t('filters.fusioned', 'Fusioned')}
        activeCount={selectedFusioned.size}
      >
        <CompactIconFilter
          options={BOOLEAN_FILTER_OPTIONS}
          selectedOptions={selectedFusioned}
          onSelectionChange={setSelectedFusioned}
          getLabel={(v) => v}
          columns={5}
        />
      </FilterSection>

      <FilterSection
        title={t('filters.themePackExclusive', 'Theme Pack Exclusive')}
        activeCount={selectedExclusive.size}
      >
        <CompactIconFilter
          options={BOOLEAN_FILTER_OPTIONS}
          selectedOptions={selectedExclusive}
          onSelectionChange={setSelectedExclusive}
          getLabel={(v) => v}
          columns={5}
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

      <FilterSection
        title={t('filters.additionalKeyword', 'Additional Keywords')}
        activeCount={selectedBattleKeywords.size}
      >
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <BattleKeywordDropdown
            entityType="egoGift"
            selectedBattleKeywords={selectedBattleKeywords}
            onSelectionChange={setSelectedBattleKeywords}
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
        <SearchBar
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
        selectedBattleKeywords={selectedBattleKeywords}
        selectedDifficulties={selectedDifficulties}
        selectedTiers={selectedTiers}
        selectedThemePacks={selectedThemePacks}
        selectedAttributeTypes={selectedAttributeTypes}
        selectedFusioned={selectedFusioned}
        selectedExclusive={selectedExclusive}
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
