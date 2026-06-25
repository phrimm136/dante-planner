import { useState, Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useThemePackListData } from '@/pages/themePack'
import { FilterPageLayout } from '@/components/filter/FilterPageLayout'
import { FilterSection } from '@/components/filter/FilterSection'
import { SearchableMultiSelect } from '@/components/common/SearchableMultiSelect'
import { EgoGiftSearchDropdown } from '@/components/filter/EgoGiftSearchDropdown'
import { AbEventList, useAbEventListSpec } from '@/pages/abEvent'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Theme Pack searchable dropdown with i18n names.
 */
function ThemePackSearchDropdown({
  selectedThemePacks,
  onSelectionChange,
}: {
  selectedThemePacks: Set<string>
  onSelectionChange: (packs: Set<string>) => void
}) {
  const { t } = useTranslation('database')
  const { spec, i18n: themePackI18n } = useThemePackListData()

  const options = useMemo(() =>
    Object.keys(spec).map((id) => ({
      value: id,
      label: themePackI18n[id]?.name ?? id,
    })),
    [spec, themePackI18n]
  )

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selectedThemePacks}
      onSelectionChange={onSelectionChange}
      placeholder={t('filters.themePack', 'Theme Pack')}
      searchPlaceholder={t('filters.searchThemePack', 'Search Theme Packs...')}
    />
  )
}

/**
 * Shell component - loads spec, manages filter states.
 */
function AbEventPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useAbEventListSpec()

  // Filter states
  const [selectedEgoGifts, setSelectedEgoGifts] = useState<Set<string>>(new Set())
  const [selectedThemePacks, setSelectedThemePacks] = useState<Set<string>>(new Set())

  const handleResetAll = () => {
    setSelectedEgoGifts(new Set())
    setSelectedThemePacks(new Set())
  }

  const activeFilterCount =
    selectedEgoGifts.size +
    selectedThemePacks.size

  const primaryFilters = (
    <FilterSection
      title={t('filters.egoGift', 'EGO Gift')}
      activeCount={selectedEgoGifts.size}
    >
      <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
        <EgoGiftSearchDropdown
          selectedEgoGifts={selectedEgoGifts}
          onSelectionChange={setSelectedEgoGifts}
        />
      </Suspense>
    </FilterSection>
  )

  const secondaryFilters = (
    <FilterSection
      title={t('filters.themePack', 'Theme Pack')}
      activeCount={selectedThemePacks.size}
    >
      <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
        <ThemePackSearchDropdown
          selectedThemePacks={selectedThemePacks}
          onSelectionChange={setSelectedThemePacks}
        />
      </Suspense>
    </FilterSection>
  )

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
    >
      <AbEventList
        spec={spec}
        selectedEgoGifts={selectedEgoGifts}
        selectedThemePacks={selectedThemePacks}
      />
    </FilterPageLayout>
  )
}

/**
 * AbEventPage - Abnormality event browser with responsive filter sidebar
 *
 * Granular loading architecture:
 * - Outer Suspense: ListPageSkeleton for spec loading (initial)
 * - EGO Gift / Theme Pack dropdowns: Own Suspense for i18n
 * - AbEventList: Uses deferred hook for name search
 */
export default function AbEventPage() {
  return (
    <div className="container mx-auto p-8">
      <Suspense fallback={<ListPageSkeleton preset="abEvent" />}>
        <AbEventPageShell />
      </Suspense>
    </div>
  )
}
