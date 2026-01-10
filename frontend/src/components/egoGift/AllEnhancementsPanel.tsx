/**
 * AllEnhancementsPanel - Displays all enhancement levels stacked vertically
 *
 * Shows base, +, and ++ enhancement descriptions in a single view.
 * Renders structure for all levels up to maxEnhancement, with descriptions in Suspense.
 *
 * Pattern Source: PassiveI18n.tsx (granular Suspense per text element)
 */

import { Suspense } from 'react'
import { getEGOGiftEnhancementIconPath, getEGOGiftCostIconPath } from '@/lib/assetPaths'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { Skeleton } from '@/components/ui/skeleton'
import { ENHANCEMENT_LABELS, ENHANCEMENT_LEVELS, type EnhancementLevel } from '@/lib/constants'

interface AllEnhancementsPanelProps {
  /** Maximum enhancement level to display (0, 1, or 2) */
  maxEnhancement: EnhancementLevel
  /** Array of costs per level (null if enhancement not available for that level) */
  costs: (number | null)[]
  /** Array of description strings (empty strings render as empty text) */
  descriptions?: string[]
}

/**
 * Single enhancement row - displays structure with Suspense for description
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
      {/* Structure - always visible (icon + cost) */}
      <div className="flex items-center gap-3 mb-3">
        {/* Enhancement Level Icon */}
        <div className="h-12 w-12 flex items-center justify-center bg-muted">
          {level === 0 ? (
            <span className="text-lg font-bold">{ENHANCEMENT_LABELS[level]}</span>
          ) : (
            <img
              src={getEGOGiftEnhancementIconPath(level)}
              alt={ENHANCEMENT_LABELS[level]}
              className="h-6 w-12 object-contain"
            />
          )}
        </div>

        {/* Enhancement Cost */}
        {cost !== null && (
          <div className="flex items-center gap-2">
            <img
              src={getEGOGiftCostIconPath()}
              alt="Cost"
              className="w-6 h-6"
            />
            <span className="text-sm font-semibold">{cost}</span>
          </div>
        )}
      </div>

      {/* Description - in Suspense to isolate useSkillTagI18n suspend */}
      <div className="text-sm">
        <Suspense fallback={<Skeleton className="h-24 w-full" />}>
          <FormattedDescription text={description} />
        </Suspense>
      </div>
    </div>
  )
}

export function AllEnhancementsPanel({
  maxEnhancement,
  descriptions = [],
  costs
}: AllEnhancementsPanelProps) {
  // Render all levels from 0 to maxEnhancement (structure stays visible)
  const levelsToRender = ENHANCEMENT_LEVELS.filter(level => level <= maxEnhancement)

  return (
    <div className="border rounded-lg p-4 space-y-4">
      {levelsToRender.map((level, index) => (
        <EnhancementRow
          key={level}
          level={level}
          description={descriptions[level] ?? ''}
          cost={costs[level]}
          isLast={index === levelsToRender.length - 1}
        />
      ))}
    </div>
  )
}

export default AllEnhancementsPanel
