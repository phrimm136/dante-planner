import { memo } from 'react'
import type { EnhancementLevel } from '@/lib/constants'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { EGOGiftTooltipContent } from './EGOGiftTooltipContent'
import { cn } from '@/lib/utils'

interface EGOGiftTooltipProps {
  /** Trigger element (button, div, etc.) */
  children: React.ReactNode
  /** Gift ID for tooltip content */
  giftId: string
  /** Enhancement level (0, 1, or 2) */
  enhancement?: EnhancementLevel
  /** Tooltip placement side */
  side?: 'top' | 'right' | 'bottom' | 'left'
  /** Additional className for TooltipContent */
  className?: string
}

// Custom comparison - compare by giftId and enhancement only
// children excluded (React elements), side/className rarely change
function areEGOGiftTooltipPropsEqual(
  prev: EGOGiftTooltipProps,
  next: EGOGiftTooltipProps
): boolean {
  return (
    prev.giftId === next.giftId &&
    prev.enhancement === next.enhancement &&
    prev.side === next.side &&
    prev.className === next.className
    // children excluded - comparing React elements is expensive and unreliable
  )
}

/**
 * Standardized tooltip wrapper for EGO gift cards
 * Provides consistent styling across all gift tooltips
 */
export const EGOGiftTooltip = memo(function EGOGiftTooltip({
  children,
  giftId,
  enhancement = 0,
  side = 'bottom',
  className,
}: EGOGiftTooltipProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {children}
      </TooltipTrigger>
      <TooltipContent
        side={side}
        className={cn(
          'w-auto max-w-[330px] bg-black/85 border-neutral-800 text-foreground rounded-none p-2',
          className
        )}
      >
        <EGOGiftTooltipContent giftId={giftId} enhancement={enhancement} />
      </TooltipContent>
    </Tooltip>
  )
}, areEGOGiftTooltipPropsEqual)
