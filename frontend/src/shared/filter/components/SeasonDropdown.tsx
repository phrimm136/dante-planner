import { useTranslation } from 'react-i18next'

import { SEASONS, type Season } from '@/shared/gameData'
import { useFilterI18nData } from '../hooks/useFilterI18nData'
import { SearchableMultiSelect } from './SearchableMultiSelect'

interface SeasonDropdownProps {
  selected: Set<Season>
  onSelectionChange: (seasons: Set<Season>) => void
  counts?: Record<string, number>
  className?: string
}

export function SeasonDropdown({
  selected,
  onSelectionChange,
  counts,
  className,
}: SeasonDropdownProps) {
  const { t } = useTranslation(['database', 'common'])
  const { seasonsI18n } = useFilterI18nData()

  const options = SEASONS.map((season) => ({
    value: String(season),
    label: seasonsI18n[`${season}`] || `Season ${season}`,
    count: counts?.[String(season)],
  }))

  const selectedStrings = new Set(Array.from(selected).map(String))

  const handleChange = (values: Set<string>) => {
    onSelectionChange(new Set(Array.from(values).map(Number) as Season[]))
  }

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selectedStrings}
      onSelectionChange={handleChange}
      placeholder={t('filters.season', 'Season')}
      searchPlaceholder={t('filters.searchSeason', 'Search Seasons...')}
      sortByLabel={false}
      className={className}
    />
  )
}
