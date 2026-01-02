import { useState, Suspense, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EGOGiftDifficulty, EGOGiftTier, EGOGiftAttributeType } from '@/lib/constants'
import { FilterPageLayout } from '@/components/common/FilterPageLayout'
import { FilterSection } from '@/components/common/FilterSection'
import { CompactEGOGiftKeywordFilter } from '@/components/egoGift/CompactEGOGiftKeywordFilter'
import { CompactDifficultyFilter } from '@/components/egoGift/CompactDifficultyFilter'
import { CompactTierFilter } from '@/components/egoGift/CompactTierFilter'
import { ThemePackDropdown } from '@/components/common/ThemePackDropdown'
import { CompactAttributeTypeFilter } from '@/components/common/CompactAttributeTypeFilter'
import { EGOGiftSearchBar } from '@/components/egoGift/EGOGiftSearchBar'
import { EGOGiftList } from '@/components/egoGift/EGOGiftList'
import { LoadingState } from '@/components/common/LoadingState'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function EGOGiftPageContent() {
  const { t } = useTranslation()
  const { spec, i18n } = useEGOGiftListData()

  // Merge spec and i18n into EGOGiftListItem array
  const gifts = useMemo<EGOGiftListItem[]>(() =>
    Object.entries(spec).map(([id, specData]) => ({
      id,
      name: i18n[id] || id,
      tag: specData.tag as EGOGiftListItem['tag'],
      keyword: specData.keyword,
      attributeType: specData.attributeType,
      themePack: specData.themePack,
      hardOnly: specData.hardOnly,
      extremeOnly: specData.extremeOnly,
    })),
    [spec, i18n]
  )

  // Filter states
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [selectedDifficulties, setSelectedDifficulties] = useState<Set<EGOGiftDifficulty>>(new Set())
  const [selectedTiers, setSelectedTiers] = useState<Set<EGOGiftTier>>(new Set())
  const [selectedThemePacks, setSelectedThemePacks] = useState<Set<string>>(new Set())
  const [selectedAttributeTypes, setSelectedAttributeTypes] = useState<Set<EGOGiftAttributeType>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Calculate active filter count for mobile badge
  const activeFilterCount =
    selectedKeywords.size +
    selectedDifficulties.size +
    selectedTiers.size +
    selectedThemePacks.size +
    selectedAttributeTypes.size

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
        <ThemePackDropdown
          selectedThemePacks={selectedThemePacks}
          onThemePacksChange={setSelectedThemePacks}
        />
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
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.egoGift.title', 'EGO Gifts')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.egoGift.description', 'Browse and search EGO Gifts')}
      </p>

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
          />
        }
      >
        <EGOGiftList
          gifts={gifts}
          selectedKeywords={selectedKeywords}
          selectedDifficulties={selectedDifficulties}
          selectedTiers={selectedTiers}
          selectedThemePacks={selectedThemePacks}
          selectedAttributeTypes={selectedAttributeTypes}
          searchQuery={searchQuery}
        />
      </FilterPageLayout>
    </div>
  )
}

/**
 * EGOGiftPage - EGO Gift browser with responsive filter sidebar
 *
 * Uses FilterPageLayout for responsive desktop sidebar / mobile sheet layout
 */
export default function EGOGiftPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <EGOGiftPageContent />
    </Suspense>
  )
}
