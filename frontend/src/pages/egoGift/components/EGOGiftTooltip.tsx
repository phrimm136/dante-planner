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

/**
 * Standardized tooltip wrapper for EGO gift cards
 * Provides consistent styling across all gift tooltips
 */
export function EGOGiftTooltip({
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
          'w-auto max-w-[330px] bg-background border-1 border-primary text-foreground rounded-none p-2',
          className
        )}
        onClick={(e) => { e.stopPropagation() }}
        onMouseDown={(e) => { e.stopPropagation() }}
      >
        <EGOGiftTooltipContent giftId={giftId} enhancement={enhancement} />
      </TooltipContent>
    </Tooltip>
  )
}
