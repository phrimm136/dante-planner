import { useTranslation } from 'react-i18next'
import { useEGOGiftListSpec } from '@/pages/egoGift'
import { toEGOGiftEntity } from './lib/egoGiftEntity'

import type { EGOGiftEntity, EGOGiftSpecListSchema, EGOGiftFacetState } from '@/pages/egoGift'
import type { z } from 'zod'
import { BOOLEAN_FILTER_OPTIONS } from '@/lib/constants'
import { calculateActiveFilterCount } from '@/shared/filter'
import { useFilterStore } from '@/components/hooks/filterStore'
import type { FilterStore } from '@/components/hooks/filterStore'
import { egoGiftFilterStore } from './stores/egoGiftFilterStore'
import { EntityListPage } from '@/shared/filter'
import { FilterPageLayout } from '@/shared/filter'
import { FilterSectionList, filterSection } from '@/shared/filter'
import { EGOGiftKeywordFilter } from '@/pages/egoGift'
import { DifficultyFilter } from '@/pages/egoGift'
import { TierFilter } from '@/pages/egoGift'
import { ThemePackFilterDropdown } from '@/pages/themePack'
import { BattleKeywordDropdown } from '@/shared/filter'
import { AttributeTypeFilter } from '@/shared/filter'
import { IconFilter } from '@/shared/filter'
import { SearchBar } from '@/shared/filter'
import { EGOGiftList } from '@/pages/egoGift'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'

function BooleanFilter({
  selected,
  onSelectionChange,
}: {
  selected: Set<string>
  onSelectionChange: (options: Set<string>) => void
}) {
  return (
    <IconFilter
      options={BOOLEAN_FILTER_OPTIONS}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getLabel={(v) => v}
    />
  )
}

function EGOGiftCardGrid({
  spec,
  store,
}: {
  spec: z.infer<typeof EGOGiftSpecListSchema>
  store: FilterStore<EGOGiftFacetState>
}) {
  const gifts: EGOGiftEntity[] = Object.entries(spec).map(([id, entry]) =>
    toEGOGiftEntity(id, entry),
  )

  return <EGOGiftList gifts={gifts} store={store} />
}

function EGOGiftPageShell() {
  const { t } = useTranslation(['database', 'common'])
  const spec = useEGOGiftListSpec()

  const {
    values: filters,
    setters,
    searchQuery,
    setSearchQuery,
    resetAll,
    store,
  } = useFilterStore(egoGiftFilterStore)

  const activeFilterCount = calculateActiveFilterCount(...Object.values(filters))

  const PRIMARY_FILTERS = [
    filterSection({
      key: 'selectedKeywords',
      titleKey: 'filters.keyword',
      titleFallback: 'Keyword',
      Component: EGOGiftKeywordFilter,
      selected: filters.selectedKeywords,
      onSelectionChange: setters.selectedKeywords,
    }),
    filterSection({
      key: 'selectedDifficulties',
      titleKey: 'filters.difficulty',
      titleFallback: 'Difficulty',
      Component: DifficultyFilter,
      selected: filters.selectedDifficulties,
      onSelectionChange: setters.selectedDifficulties,
    }),
  ]

  const SECONDARY_FILTERS = [
    filterSection({
      key: 'selectedTiers',
      titleKey: 'filters.tier',
      titleFallback: 'Tier',
      Component: TierFilter,
      selected: filters.selectedTiers,
      onSelectionChange: setters.selectedTiers,
    }),
    filterSection({
      key: 'selectedAttributeTypes',
      titleKey: 'filters.attributeType',
      titleFallback: 'Attribute',
      Component: AttributeTypeFilter,
      selected: filters.selectedAttributeTypes,
      onSelectionChange: setters.selectedAttributeTypes,
    }),
    filterSection({
      key: 'selectedFusioned',
      titleKey: 'filters.fusioned',
      titleFallback: 'Fusioned',
      Component: BooleanFilter,
      selected: filters.selectedFusioned,
      onSelectionChange: setters.selectedFusioned,
    }),
    filterSection({
      key: 'selectedExclusive',
      titleKey: 'filters.themePackExclusive',
      titleFallback: 'Theme Pack Exclusive',
      Component: BooleanFilter,
      selected: filters.selectedExclusive,
      onSelectionChange: setters.selectedExclusive,
    }),
    filterSection({
      key: 'selectedThemePacks',
      titleKey: 'filters.themePack',
      titleFallback: 'Theme Pack',
      suspense: true,
      Component: ThemePackFilterDropdown,
      selected: filters.selectedThemePacks,
      onSelectionChange: setters.selectedThemePacks,
    }),
    filterSection({
      key: 'selectedBattleKeywords',
      titleKey: 'filters.additionalKeyword',
      titleFallback: 'Additional Keywords',
      suspense: true,
      Component: BattleKeywordDropdown,
      selected: filters.selectedBattleKeywords,
      onSelectionChange: setters.selectedBattleKeywords,
      props: { entityType: 'egoGift' as const },
    }),
  ]

  return (
    <FilterPageLayout
      primaryFilters={<FilterSectionList sections={PRIMARY_FILTERS} />}
      secondaryFilters={<FilterSectionList sections={SECONDARY_FILTERS} />}
      activeFilterCount={activeFilterCount}
      onResetAll={resetAll}
      searchBar={
        <SearchBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          placeholder={t('pages.egoGift.searchBar', 'Search EGO Gifts...')}
        />
      }
    >
      <EGOGiftCardGrid spec={spec} store={store} />
    </FilterPageLayout>
  )
}

export default function EGOGiftPage() {
  return (
    <EntityListPage skeleton={<ListPageSkeleton geometry={EGO_GIFT_GEOMETRY} />}>
      <EGOGiftPageShell />
    </EntityListPage>
  )
}
