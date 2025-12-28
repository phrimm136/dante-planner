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
  /** Whether the card is selected */
  isSelected?: boolean
  /** Additional CSS classes for styling flexibility */
  className?: string
}

/**
 * Pure view-only component for rendering an EGO gift card (96x96px).
 * Does NOT include any interaction logic (Link, onClick, tooltip, etc.)
 * Parent component is responsible for wrapping with Tooltip, button, or other interactive elements.
 *
 * @example
 * // With tooltip and click handler (selection grid)
 * <Tooltip>
 *   <TooltipTrigger asChild>
 *     <button onClick={handleSelect}>
 *       <EGOGiftCard gift={gift} isSelected={true} />
 *     </button>
 *   </TooltipTrigger>
 *   <TooltipContent>
 *     <EGOGiftTooltipContent giftId={gift.id} enhancement={0} />
 *   </TooltipContent>
 * </Tooltip>
 */
export function EGOGiftCard({
  gift,
  enhancement = 0,
  isSelected = false,
  className,
}: EGOGiftCardProps) {
  const { id } = gift

  // Extract tier from tag array (guaranteed to exist)
  const tier = gift.tag.find((t) => t.startsWith('TIER_'))!.replace('TIER_', '')

  return (
    <div className={cn('w-24 relative', className)}>
      {/* Background and icon container */}
      <div className="relative w-24 h-24">
        {/* Background layers */}
        <EGOGiftCardBackground enhancement={enhancement} size="mini" />

        {/* Gift Icon - centered */}
        <img
          src={getEGOGiftIconPath(id)}
          alt={gift.name}
          className="absolute inset-0 w-full h-full object-contain"
          loading="lazy"
        />

        {/* Selection highlight overlay */}
        {isSelected && (
          <img
            src="/images/UI/egoGift/onSelect.webp"
            alt="Selected"
            className="absolute inset-0 w-full h-full object-contain pointer-events-none"
            loading="lazy"
          />
        )}

        {/* Tier Indicator - Upper-left */}
        <EGOGiftTierIndicator tier={tier} />

        {/* Enhancement Indicator - Upper-right */}
        <EGOGiftEnhancementIndicator enhancement={enhancement} />

        {/* Keyword Icon - Lower-right */}
        <EGOGiftKeywordIndicator keyword={gift.keyword} />
      </div>
    </div>
  )
}
