import { getEGOGiftIconPath } from '@/lib/assetPaths'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { EGOGiftCardBackground } from './EGOGiftCardBackground'
import { EGOGiftTierIndicator } from './EGOGiftTierIndicator'
import { EGOGiftEnhancementIndicator } from './EGOGiftEnhancementIndicator'
import { EGOGiftKeywordIndicator } from './EGOGiftKeywordIndicator'
import { cn } from '@/lib/utils'

interface EGOGiftCardProps {
  /** The EGO gift data to display */
  gift: EGOGiftListItem
  /** Enhancement level (0, 1, or 2) */
  enhancement?: 0 | 1 | 2
  /** Additional CSS classes for styling flexibility */
  className?: string
}

/**
 * Pure view-only component for rendering an EGO gift card.
 * Does NOT include any interaction logic (Link, onClick, etc.)
 * Parent component is responsible for wrapping with Link, button, or other interactive elements.
 *
 * @example
 * // As a link (use EGOGiftCardLink)
 * <EGOGiftCardLink gift={gift} />
 *
 * // With custom wrapper
 * <button onClick={handleSelect}>
 *   <EGOGiftCard gift={gift} enhancement={1} />
 * </button>
 */
export function EGOGiftCard({
  gift,
  enhancement = 0,
  className,
}: EGOGiftCardProps) {
  const { id } = gift

  // Extract tier from tag array (guaranteed to exist)
  const tier = gift.tag.find(t => t.startsWith('TIER_'))!.replace('TIER_', '')

  return (
    <div className={cn('w-32 relative', className)}>
      {/* Background and icon container */}
      <div className="relative w-32 h-32">
        {/* Background layers */}
        <EGOGiftCardBackground enhancement={enhancement} size="full" />

        {/* Gift Icon - centered */}
        <img
          src={getEGOGiftIconPath(id)}
          alt={gift.name}
          className="absolute inset-0 w-full h-full object-contain"
        />

        {/* Tier Indicator - Upper-left */}
        <EGOGiftTierIndicator tier={tier} />

        {/* Enhancement Indicator - Upper-right */}
        <EGOGiftEnhancementIndicator enhancement={enhancement} />

        {/* Keyword Icon - Lower-right */}
        <EGOGiftKeywordIndicator keyword={gift.keyword} />
      </div>

      {/* Name below icon */}
      <h3 className="font-semibold text-sm text-center line-clamp-2 w-full mt-2">
        {gift.name}
      </h3>
    </div>
  )
}
