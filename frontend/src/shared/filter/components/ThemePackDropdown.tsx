import { useTranslation } from 'react-i18next'

import { SearchableMultiSelect } from './SearchableMultiSelect'

interface ThemePackDropdownProps {
  selected: Set<string>
  onThemePacksChange: (themePacks: Set<string>) => void
  /** Structural shape only — the dropdown reads each pack's gift-pool length for the count. */
  packs: Record<string, { specificEgoGiftPool?: unknown[] }>
  names: Record<string, { name?: string } | undefined>
}

/**
 * Multi-select searchable dropdown for theme pack filtering.
 *
 * The themePack slice fetches packs/names and renders this inside its own
 * Suspense boundary, keeping `shared/filter` free of any `@/pages/*` import
 * (sink rule).
 *
 * Pattern: Follows SeasonDropdown.tsx structure
 */
export function ThemePackDropdown({
  selected,
  onThemePacksChange,
  packs,
  names,
}: ThemePackDropdownProps) {
  const { t } = useTranslation(['database', 'common'])

  const options = Object.entries(packs).map(([themePackId, packData]) => ({
    value: themePackId,
    label: names[themePackId]?.name ?? `Theme Pack ${themePackId}`,
    count: packData.specificEgoGiftPool?.length,
  }))

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selected}
      onSelectionChange={onThemePacksChange}
      placeholder={t('filters.themePack', 'Theme Pack')}
      searchPlaceholder={t('filters.searchThemePack', 'Search theme packs...')}
    />
  )
}
