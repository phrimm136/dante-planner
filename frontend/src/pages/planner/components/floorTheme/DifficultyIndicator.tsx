import { DIFFICULTY_LABELS, type DifficultyLabel } from '@/shared/gameData'
import { cn } from '@/lib/utils'
import { DIFFICULTY_COLORS, SECTION_STYLES } from '@/lib/constants'

interface DifficultyIndicatorProps {
  difficulty: DifficultyLabel | null
  className?: string
}

export function DifficultyIndicator({ difficulty, className }: DifficultyIndicatorProps) {
  if (!difficulty) {
    return (
      <div className={cn('h-6 flex items-center', className)}>
        <span className={SECTION_STYLES.TEXT.caption}>-</span>
      </div>
    )
  }

  const color = DIFFICULTY_COLORS[difficulty]

  return (
    <div className={cn('h-6 flex items-center justify-center', className)}>
      <span className="text-sm font-semibold tracking-wide" style={{ color }}>
        {difficulty}
      </span>
    </div>
  )
}

export function getFloorDifficultyLabel(
  floorNumber: number,
  baseDifficulty: 'NORMAL' | 'HARD',
): DifficultyLabel {
  if (floorNumber >= 11) {
    return DIFFICULTY_LABELS.EXTREME_MIRROR
  }
  if (floorNumber >= 6) {
    return DIFFICULTY_LABELS.INFINITY_MIRROR
  }
  return baseDifficulty === 'HARD' ? DIFFICULTY_LABELS.HARD : DIFFICULTY_LABELS.NORMAL
}
