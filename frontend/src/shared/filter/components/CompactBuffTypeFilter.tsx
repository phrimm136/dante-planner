import { useTranslation } from 'react-i18next'
import { CompactIconFilter } from './CompactIconFilter'
import { BUFF_TYPES } from '@/shared/gameData'

import type { BuffType } from '@/shared/gameData'

interface CompactBuffTypeFilterProps {
  selectedBuffTypes: Set<BuffType>
  onBuffTypesChange: (types: Set<BuffType>) => void
}

/**
 * Compact buff type filter for keyword filter sidebar.
 * Three text-label buttons: Positive, Negative, Neutral.
 *
 * Pattern: Wraps CompactIconFilter in text mode (no getIconPath)
 * like CompactAttributeTypeFilter wraps it in icon mode.
 */
export function CompactBuffTypeFilter({
  selectedBuffTypes,
  onBuffTypesChange,
}: CompactBuffTypeFilterProps) {
  const { t } = useTranslation('database')

  const getLabel = (option: string): string => {
    const key = option.toLowerCase()
    return t(`keyword.${key}`)
  }

  return (
    <CompactIconFilter
      options={BUFF_TYPES}
      selectedOptions={selectedBuffTypes as Set<string>}
      onSelectionChange={(types) => {
        onBuffTypesChange(types as Set<BuffType>)
      }}
      getLabel={getLabel}
      columns={3}
    />
  )
}
