import { useTranslation } from 'react-i18next'
import { OptionToggleGroup, type ToggleOption } from '@/pages/battle'
import { AB_EVENT_RESULT_TYPES } from '@/shared/gameData'

import type { AbEventResultType } from '@/shared/gameData'

interface AbEventResultFilterProps {
  selected: Set<AbEventResultType>
  onSelectionChange: (resultTypes: Set<AbEventResultType>) => void
}

export function AbEventResultFilter({ selected, onSelectionChange }: AbEventResultFilterProps) {
  const { t } = useTranslation('database')

  const options: ToggleOption<AbEventResultType>[] = AB_EVENT_RESULT_TYPES.map((resultType) => ({
    value: resultType,
    label: t(`filters.eventResultTypes.${resultType}`),
  }))

  return (
    <OptionToggleGroup
      options={options}
      selected={selected}
      onSelectionChange={onSelectionChange}
    />
  )
}
