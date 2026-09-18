import type { EnhancementLevel } from '@/shared/gameData'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { EGOGiftTooltipContent } from './EGOGiftTooltipContent'
import { cn } from '@/lib/utils'

interface EGOGiftTooltipProps {
  children: React.ReactNode
  giftId: string
  enhancement?: EnhancementLevel
  side?: 'top' | 'right' | 'bottom' | 'left'
  className?: string
}

export function EGOGiftTooltip({
  children,
  giftId,
  enhancement = 0,
  side = 'bottom',
  className,
}: EGOGiftTooltipProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent
        side={side}
        className={cn(
          'w-auto max-w-[330px] bg-background border-1 border-primary text-foreground rounded-none p-2',
          className,
        )}
        onClick={(e) => {
          e.stopPropagation()
        }}
        onMouseDown={(e) => {
          e.stopPropagation()
        }}
      >
        <EGOGiftTooltipContent giftId={giftId} enhancement={enhancement} />
      </TooltipContent>
    </Tooltip>
  )
}
