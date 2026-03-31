import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { useThemePackListData } from '@/hooks/useThemePackListData'
import { SearchableMultiSelect } from '@/components/common/SearchableMultiSelect'

interface ThemePackDropdownProps {
  selectedThemePacks: Set<string>
  onThemePacksChange: (themePacks: Set<string>) => void
}

/**
 * Multi-select searchable dropdown for theme pack filtering.
 *
 * Fetches theme pack data internally - wrap in Suspense boundary.
 *
 * Pattern: Follows SeasonDropdown.tsx structure
 */
export function ThemePackDropdown({
  selectedThemePacks,
  onThemePacksChange,
}: ThemePackDropdownProps) {
  const { t } = useTranslation(['database', 'common'])
  const { spec: themePackList, i18n: themePackI18n } = useThemePackListData()

  const options = useMemo(
    () =>
      Object.entries(themePackList).map(([themePackId, packData]) => ({
        value: themePackId,
        label: themePackI18n[themePackId]?.name ?? `Theme Pack ${themePackId}`,
        count: packData.specificEgoGiftPool?.length,
      })),
    [themePackList, themePackI18n]
  )

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selectedThemePacks}
      onSelectionChange={onThemePacksChange}
      placeholder={t('filters.themePack', 'Theme Pack')}
      searchPlaceholder={t('filters.searchThemePack', 'Search theme packs...')}
    />
  )
}
