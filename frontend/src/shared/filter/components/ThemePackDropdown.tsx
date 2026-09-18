import { useTranslation } from 'react-i18next'

import { SearchableMultiSelect } from './SearchableMultiSelect'

interface ThemePackDropdownProps {
  selected: Set<string>
  onSelectionChange: (themePacks: Set<string>) => void
  packs: Record<string, { specificEgoGiftPool?: unknown[] }>
  names: Record<string, { name?: string } | undefined>
}

export function ThemePackDropdown({
  selected,
  onSelectionChange,
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
      onSelectionChange={onSelectionChange}
      placeholder={t('filters.themePack', 'Theme Pack')}
      searchPlaceholder={t('filters.searchThemePack', 'Search theme packs...')}
    />
  )
}
