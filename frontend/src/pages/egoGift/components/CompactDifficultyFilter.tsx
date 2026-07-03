import { CompactIconFilter } from '@/shared/filter'
import { EGO_GIFT_DIFFICULTIES } from '@/shared/gameData'

import type { EGOGiftDifficulty } from '@/shared/gameData'

/** Display labels for difficulty values (capitalized) */
const DIFFICULTY_LABELS: Record<EGOGiftDifficulty, string> = {
  normal: 'Normal',
  hard: 'Hard',
  extreme: 'Extreme',
}

interface CompactDifficultyFilterProps {
  selectedDifficulties: Set<EGOGiftDifficulty>
  onSelectionChange: (difficulties: Set<EGOGiftDifficulty>) => void
}

/**
 * Compact difficulty text filter for filter sidebar
 * 3 text buttons displayed in a flex row
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Pattern: Wraps CompactIconFilter in text mode (no getIconPath)
 */
export function CompactDifficultyFilter({
  selectedDifficulties,
  onSelectionChange,
}: CompactDifficultyFilterProps) {
  return (
    <CompactIconFilter
      options={EGO_GIFT_DIFFICULTIES}
      selectedOptions={selectedDifficulties as Set<string>}
      onSelectionChange={(options) => {
        onSelectionChange(options as Set<EGOGiftDifficulty>)
      }}
      getLabel={(difficulty) => DIFFICULTY_LABELS[difficulty as EGOGiftDifficulty]}
      columns={5}
    />
  )
}
