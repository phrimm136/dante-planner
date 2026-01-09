import { Suspense } from 'react'
import { getEGOGiftIconPath } from '@/lib/assetPaths'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { EGOGiftCardBackground } from './EGOGiftCardBackground'
import { EGOGiftTierIndicator } from './EGOGiftTierIndicator'
import { EGOGiftEnhancementIndicator } from './EGOGiftEnhancementIndicator'
import { EGOGiftKeywordIndicator } from './EGOGiftKeywordIndicator'
import { EGOGiftName } from './EGOGiftName'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

interface EGOGiftCardProps {
  /** The EGO gift data to display */
  gift: EGOGiftListItem
  /** Enhancement level (0, 1, or 2) */
  enhancement?: 0 | 1 | 2
  /** Whether the card is selected */
  isSelected?: boolean
  /** Whether to show the gift name below the card */
  showName?: boolean
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
  showName = false,
  className,
}: EGOGiftCardProps) {
  const { id } = gift

  // Extract tier from tag array (guaranteed to exist)
  const tier = gift.tag.find((t) => t.startsWith('TIER_'))!.replace('TIER_', '')

  return (
    <div className={cn('w-24', showName ? 'flex flex-col items-center gap-1.5' : 'relative', className)}>
      {/* Background and icon container */}
      <div className={cn('w-24 h-24', showName && 'relative')}>
        {/* Background layers */}
        <EGOGiftCardBackground enhancement={enhancement} size="mini" />

        {/* Gift Icon - centered */}
        <img
          src={getEGOGiftIconPath(id)}
          alt={`EGO Gift ${id}`}
          className="absolute inset-0 m-auto w-20 h-20"
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

      {/* Name below card - matches Identity/EGO pattern with internal Suspense */}
      {showName && (
        <span className="text-xs text-center text-foreground line-clamp-2 w-24 leading-tight font-medium">
          <Suspense fallback={<Skeleton className="h-5 w-24 bg-foreground" />}>
            <EGOGiftName id={id} />
          </Suspense>
        </span>
      )}
    </div>
  )
}
