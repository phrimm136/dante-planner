import { IconFilter } from '@/shared/filter'
import { EGO_GIFT_DIFFICULTIES } from '@/shared/gameData'

import type { EGOGiftDifficulty } from '@/shared/gameData'

const DIFFICULTY_LABELS: Record<EGOGiftDifficulty, string> = {
  normal: 'Normal',
  hard: 'Hard',
  extreme: 'Extreme',
}

interface DifficultyFilterProps {
  selected: Set<EGOGiftDifficulty>
  onSelectionChange: (difficulties: Set<EGOGiftDifficulty>) => void
}

export function DifficultyFilter({ selected, onSelectionChange }: DifficultyFilterProps) {
  return (
    <IconFilter
      options={EGO_GIFT_DIFFICULTIES}
      selectedOptions={selected as Set<string>}
      onSelectionChange={(options) => {
        onSelectionChange(options as Set<EGOGiftDifficulty>)
      }}
      getLabel={(difficulty) => DIFFICULTY_LABELS[difficulty as EGOGiftDifficulty]}
    />
  )
}
