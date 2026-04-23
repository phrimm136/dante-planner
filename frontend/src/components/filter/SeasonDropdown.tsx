import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { SEASONS, type Season } from '@/lib/constants'
import { useFilterI18nData } from '@/hooks/useFilterI18nData'
import { SearchableMultiSelect } from '@/components/common/SearchableMultiSelect'

interface SeasonDropdownProps {
  selectedSeasons: Set<Season>
  onSelectionChange: (seasons: Set<Season>) => void
  /** Entity count per season ID for display */
  counts?: Record<string, number>
  className?: string
}

/**
 * Multi-select searchable dropdown for season filtering.
 * Options sorted by season ID (SEASONS array order), not alphabetically.
 *
 * Fetches i18n data internally - wrap in Suspense boundary.
 */
export function SeasonDropdown({
  selectedSeasons,
  onSelectionChange,
  counts,
  className,
}: SeasonDropdownProps) {
  const { t } = useTranslation(['database', 'common'])
  const { seasonsI18n } = useFilterI18nData()

  const options = useMemo(
    () =>
      SEASONS.map((season) => ({
        value: String(season),
        label: seasonsI18n[`${season}`] || `Season ${season}`,
        count: counts?.[String(season)],
      })),
    [seasonsI18n, counts]
  )

  // Bridge string Set <-> Season Set
  const selectedStrings = useMemo(
    () => new Set(Array.from(selectedSeasons).map(String)),
    [selectedSeasons]
  )

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
