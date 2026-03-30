import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { SearchableMultiSelect } from '@/components/common/SearchableMultiSelect'

/**
 * EGO Gift searchable dropdown with i18n names.
 * Maps EGO Gift spec + name list into SearchableMultiSelect options.
 */
export function EGOGiftSearchDropdown({
  selectedEgoGifts,
  onSelectionChange,
}: {
  selectedEgoGifts: Set<string>
  onSelectionChange: (gifts: Set<string>) => void
}) {
  const { t } = useTranslation('database')
  const { spec, i18n: giftNames } = useEGOGiftListData()

  const options = useMemo(() =>
    Object.keys(spec).map((id) => ({
      value: id,
      label: giftNames[id] ?? id,
    })),
    [spec, giftNames]
  )

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selectedEgoGifts}
      onSelectionChange={onSelectionChange}
      placeholder={t('keyword.filterEgoGift')}
      searchPlaceholder={t('keyword.searchPlaceholder')}
    />
  )
}
