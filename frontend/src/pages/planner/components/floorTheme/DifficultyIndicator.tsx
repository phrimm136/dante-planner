import { DIFFICULTY_LABELS, DIFFICULTY_COLORS, type DifficultyLabel } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface DifficultyIndicatorProps {
  difficulty: DifficultyLabel | null
  className?: string
}

/**
 * Displays the floor difficulty level with appropriate color coding
 * NORMAL (yellow), HARD (orange), INFINITY MIRROR (red), EXTREME MIRROR (white)
 */
export function DifficultyIndicator({ difficulty, className }: DifficultyIndicatorProps) {
  if (!difficulty) {
    return (
      <div className={cn('h-6 flex items-center', className)}>
        <span className="text-sm text-muted-foreground">-</span>
      </div>
    )
  }

  const color = DIFFICULTY_COLORS[difficulty]

  return (
    <div className={cn('flex justify-center', className)}>
      <span
        className="text-sm font-semibold tracking-wide"
        style={{ color }}
      >
        {difficulty}
      </span>
    </div>
  )
}

/**
 * Gets the difficulty label for a floor based on floor number and user's base difficulty choice
 * @param floorNumber - 1-indexed floor number (1-15)
 * @param baseDifficulty - User's chosen difficulty for floors 1-5 (NORMAL or HARD)
 * @returns Appropriate difficulty label for display
 */
export function getFloorDifficultyLabel(
  floorNumber: number,
  baseDifficulty: 'NORMAL' | 'HARD'
): DifficultyLabel {
  if (floorNumber >= 11) {
    return DIFFICULTY_LABELS.EXTREME_MIRROR
  }
  if (floorNumber >= 6) {
    return DIFFICULTY_LABELS.INFINITY_MIRROR
  }
  return baseDifficulty === 'HARD' ? DIFFICULTY_LABELS.HARD : DIFFICULTY_LABELS.NORMAL
}
