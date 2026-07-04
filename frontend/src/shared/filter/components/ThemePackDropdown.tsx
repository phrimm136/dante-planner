import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { SearchableMultiSelect } from './SearchableMultiSelect'

/**
 * Injected theme-pack list-data hook. Structural shape only — the dropdown reads
 * each pack's gift-pool length for the count and the localized name — so
 * `shared/filter` stays free of any `@/pages/*` import (sink rule). The consumer
 * (egoGift page) passes `useThemePackListData`.
 */
type ThemePackListDataHook = () => {
  spec: Record<string, { specificEgoGiftPool?: unknown[] }>
  i18n: Record<string, { name?: string } | undefined>
}

interface ThemePackDropdownProps {
  selectedThemePacks: Set<string>
  onThemePacksChange: (themePacks: Set<string>) => void
  useListData: ThemePackListDataHook
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
  useListData,
}: ThemePackDropdownProps) {
  const { t } = useTranslation(['database', 'common'])
  const { spec: themePackList, i18n: themePackI18n } = useListData()

  const options = useMemo(
    () =>
      Object.entries(themePackList).map(([themePackId, packData]) => ({
        value: themePackId,
        label: themePackI18n[themePackId]?.name ?? `Theme Pack ${themePackId}`,
        count: packData.specificEgoGiftPool?.length,
      })),
    [themePackList, themePackI18n],
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
