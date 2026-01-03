/**
 * AllEnhancementsPanel - Displays all enhancement levels stacked vertically
 *
 * Shows base, +, and ++ enhancement descriptions in a single view.
 * Only renders levels that have descriptions (no empty panels).
 *
 * Pattern Source: EnhancementPanel.tsx (single level display structure)
 */

import { getEGOGiftEnhancementIconPath, getEGOGiftCoinIconPath } from '@/lib/assetPaths'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { ENHANCEMENT_LABELS, ENHANCEMENT_LEVELS, type EnhancementLevel } from '@/lib/constants'

interface AllEnhancementsPanelProps {
  /** Array of description strings (index 0 = base, 1 = +, 2 = ++) */
  descriptions: string[]
  /** Array of costs per level (null if enhancement not available for that level) */
  costs: (number | null)[]
}

/**
 * Single enhancement row - displays one enhancement level
 */
function EnhancementRow({
  level,
  description,
  cost,
  isLast,
}: {
  level: EnhancementLevel
  description: string
  cost: number | null
  isLast: boolean
}) {
  return (
    <div className={!isLast ? 'pb-4 border-b' : ''}>
      <div className="flex items-center gap-3 mb-3">
        {/* Enhancement Level Icon */}
        <div className="w-10 h-10 rounded flex items-center justify-center bg-muted">
          {level === 0 ? (
            <span className="text-sm font-medium">{ENHANCEMENT_LABELS[level]}</span>
          ) : (
            <img
              src={getEGOGiftEnhancementIconPath(level)}
              alt={ENHANCEMENT_LABELS[level]}
              className="w-6 h-6 object-contain"
            />
          )}
        </div>

        {/* Enhancement Cost */}
        {cost !== null && (
          <div className="flex items-center gap-2">
            <img
              src={getEGOGiftCoinIconPath()}
              alt="Cost"
              className="w-6 h-6"
            />
            <span className="text-sm font-semibold">{cost}</span>
          </div>
        )}
      </div>

      {/* Enhancement Description */}
      <div className="text-sm">
        <FormattedDescription text={description} />
      </div>
    </div>
  )
}

export function AllEnhancementsPanel({ descriptions, costs }: AllEnhancementsPanelProps) {
  // Filter to only show levels with actual descriptions
  const availableLevels = ENHANCEMENT_LEVELS.filter(
    (level) => level < descriptions.length && descriptions[level]?.trim()
  )

  return (
    <div className="border rounded-lg p-4 space-y-4">
      {availableLevels.map((level, index) => (
        <EnhancementRow
          key={level}
          level={level}
          description={descriptions[level]}
          cost={costs[level]}
          isLast={index === availableLevels.length - 1}
        />
      ))}
    </div>
  )
}

export default AllEnhancementsPanel
