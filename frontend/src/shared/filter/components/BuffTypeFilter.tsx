import { useTranslation } from 'react-i18next'
import { IconFilter } from './IconFilter'
import { BUFF_TYPES } from '@/shared/gameData'

import type { BuffType } from '@/shared/gameData'

interface BuffTypeFilterProps {
  selected: Set<BuffType>
  onBuffTypesChange: (types: Set<BuffType>) => void
}

export function BuffTypeFilter({ selected, onBuffTypesChange }: BuffTypeFilterProps) {
  const { t } = useTranslation('database')

  const getLabel = (option: string): string => {
    const key = option.toLowerCase()
    return t(`keyword.${key}`)
  }

  return (
    <IconFilter
      options={BUFF_TYPES}
      selectedOptions={selected as Set<string>}
      onSelectionChange={(types) => {
        onBuffTypesChange(types as Set<BuffType>)
      }}
      getLabel={getLabel}
    />
  )
}
